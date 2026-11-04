# AI_GENERATE_START -
from __future__ import annotations

import base64
import io
import time
import zipfile
from typing import Any
from urllib.parse import quote

import httpx

from stock_agent.config import AgentConfig


class GitHubApiError(RuntimeError):
    """GitHub API failure without credential-bearing request details."""

    def __init__(self, status_code: int, message: str) -> None:
        self.status_code = status_code
        super().__init__(f"GitHub API {status_code}: {message[:500]}")


class GitHubClient:
    """Minimal GitHub REST and Git Data API client for repair tasks."""

    def __init__(self, config: AgentConfig) -> None:
        if not config.github_token:
            raise ValueError("未配置 STOCK_AGENT_GITHUB_TOKEN")
        if "/" not in config.github_repository:
            raise ValueError("STOCK_AGENT_GITHUB_REPOSITORY 必须使用 owner/repo 格式")
        self.repository = config.github_repository
        self.workflow = config.github_workflow
        self.poll_seconds = config.repair_poll_seconds
        self.timeout_seconds = config.repair_timeout_seconds
        self._client = httpx.Client(
            base_url=config.github_api_url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {config.github_token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "stock-trading-agent",
            },
            timeout=30.0,
            follow_redirects=True,
        )

    def close(self) -> None:
        self._client.close()

    def get_run(self, run_id: int) -> dict[str, Any]:
        return self._json("GET", f"/repos/{self.repository}/actions/runs/{run_id}")

    def get_run_logs(self, run_id: int, limit: int = 120_000) -> str:
        response = self._request(
            "GET", f"/repos/{self.repository}/actions/runs/{run_id}/logs"
        )
        try:
            archive = zipfile.ZipFile(io.BytesIO(response.content))
        except zipfile.BadZipFile:
            return response.text[-limit:]
        chunks: list[str] = []
        with archive:
            for name in sorted(archive.namelist()):
                if name.endswith("/"):
                    continue
                text = archive.read(name).decode("utf-8", errors="replace")
                chunks.append(f"\n===== {name} =====\n{text}")
        return "".join(chunks)[-limit:]

    def get_branch_sha(self, branch: str) -> str:
        data = self._json(
            "GET", f"/repos/{self.repository}/branches/{quote(branch, safe='')}"
        )
        return str(data["commit"]["sha"])

    def list_files(
        self,
        ref: str,
        *,
        prefix: str = "",
        query: str = "",
        limit: int = 200,
    ) -> list[str]:
        commit = self._json("GET", f"/repos/{self.repository}/git/commits/{ref}")
        tree_sha = str(commit["tree"]["sha"])
        tree = self._json(
            "GET",
            f"/repos/{self.repository}/git/trees/{tree_sha}",
            params={"recursive": "1"},
        )
        normalized_prefix = prefix.strip().replace("\\", "/").lstrip("/")
        normalized_query = query.strip().lower()
        paths = []
        for item in tree.get("tree", []):
            path = str(item.get("path", ""))
            if item.get("type") != "blob":
                continue
            if normalized_prefix and not path.startswith(normalized_prefix):
                continue
            if normalized_query and normalized_query not in path.lower():
                continue
            paths.append(path)
            if len(paths) >= max(1, min(limit, 500)):
                break
        return paths

    def read_file(self, path: str, ref: str) -> str:
        data = self._json(
            "GET",
            f"/repos/{self.repository}/contents/{quote(path, safe='/')}",
            params={"ref": ref},
        )
        if not isinstance(data, dict) or data.get("type") != "file":
            raise GitHubApiError(400, f"不是普通文件: {path}")
        if data.get("encoding") != "base64":
            raise GitHubApiError(400, f"不支持的文件编码: {path}")
        return base64.b64decode(str(data["content"])).decode("utf-8")

    def create_branch(self, branch: str, sha: str) -> None:
        self._json(
            "POST",
            f"/repos/{self.repository}/git/refs",
            json={"ref": f"refs/heads/{branch}", "sha": sha},
            expected={201},
        )

    def commit_files(
        self,
        branch: str,
        parent_sha: str,
        changes: list[dict[str, str]],
        message: str,
    ) -> str:
        parent = self._json(
            "GET", f"/repos/{self.repository}/git/commits/{parent_sha}"
        )
        entries = []
        for change in changes:
            blob = self._json(
                "POST",
                f"/repos/{self.repository}/git/blobs",
                json={"content": change["content"], "encoding": "utf-8"},
                expected={201},
            )
            entries.append({
                "path": change["path"],
                "mode": "100644",
                "type": "blob",
                "sha": blob["sha"],
            })
        tree = self._json(
            "POST",
            f"/repos/{self.repository}/git/trees",
            json={"base_tree": parent["tree"]["sha"], "tree": entries},
            expected={201},
        )
        commit = self._json(
            "POST",
            f"/repos/{self.repository}/git/commits",
            json={
                "message": message,
                "tree": tree["sha"],
                "parents": [parent_sha],
            },
            expected={201},
        )
        self.update_branch(branch, str(commit["sha"]), force=False)
        return str(commit["sha"])

    def update_branch(self, branch: str, sha: str, *, force: bool) -> None:
        self._json(
            "PATCH",
            f"/repos/{self.repository}/git/refs/heads/{quote(branch, safe='')}",
            json={"sha": sha, "force": force},
        )

    def delete_branch(self, branch: str) -> None:
        self._request(
            "DELETE",
            f"/repos/{self.repository}/git/refs/heads/{quote(branch, safe='')}",
            expected={204},
        )

    def wait_for_workflow(self, branch: str, head_sha: str) -> dict[str, Any]:
        deadline = time.monotonic() + self.timeout_seconds
        workflow = quote(self.workflow, safe="")
        while time.monotonic() < deadline:
            data = self._json(
                "GET",
                f"/repos/{self.repository}/actions/workflows/{workflow}/runs",
                params={
                    "branch": branch,
                    "head_sha": head_sha,
                    "event": "push",
                    "per_page": 10,
                },
            )
            runs = [
                run for run in data.get("workflow_runs", [])
                if str(run.get("head_sha")) == head_sha
            ]
            if runs:
                run = runs[0]
                if run.get("status") == "completed":
                    return run
            time.sleep(self.poll_seconds)
        raise TimeoutError(f"等待 GitHub Actions 超时: {head_sha[:12]}")

    def _json(
        self,
        method: str,
        path: str,
        *,
        expected: set[int] | None = None,
        **kwargs: Any,
    ) -> dict[str, Any]:
        response = self._request(method, path, expected=expected, **kwargs)
        data = response.json()
        if not isinstance(data, dict):
            raise GitHubApiError(response.status_code, "响应不是 JSON 对象")
        return data

    def _request(
        self,
        method: str,
        path: str,
        *,
        expected: set[int] | None = None,
        **kwargs: Any,
    ) -> httpx.Response:
        try:
            response = self._client.request(method, path, **kwargs)
        except httpx.HTTPError as error:
            raise RuntimeError(f"GitHub 网络请求失败: {error.__class__.__name__}") from error
        allowed = expected or {200}
        if response.status_code not in allowed:
            try:
                payload = response.json()
                message = str(payload.get("message", "请求失败"))
            except ValueError:
                message = response.text or "请求失败"
            raise GitHubApiError(response.status_code, message)
        return response
# AI_GENERATE_END -