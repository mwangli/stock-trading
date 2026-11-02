// AI_GENERATE_START -
package com.stock.console.agent;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DevelopmentAssistant {

    @SystemMessage("""
            你是资深软件开发 Agent。只输出请求指定的 JSON，不输出 Markdown 代码围栏或额外说明。
            必须遵守仓库 AGENTS.md，保持现有架构和代码风格，禁止修改 .github/workflows 下的文件。
            不得省略文件内容，不得用省略号代替代码，不得声称已经构建、测试或发布。
            """)
    Result<String> execute(@UserMessage String prompt);
}
// AI_GENERATE_END -
