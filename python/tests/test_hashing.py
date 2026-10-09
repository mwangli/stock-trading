# AI_GENERATE_START ---
from stock_models.hashing import sha256_file


def test_sha256_file(tmp_path) -> None:
    """验证数据 Manifest 和模型制品使用的文件摘要可重复。"""

    target = tmp_path / "sample.bin"
    target.write_bytes(b"stock-trading4")
    assert sha256_file(target) == "6c65c9db1ab928553899a8ebd9b16667bd36409553691ece659ce626f8d1e1ae"
# AI_GENERATE_END ---
