// AI_GENERATE_START -
package com.stock.console.agent;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ConsoleAssistant {

    @SystemMessage("{{systemPrompt}}")
    Result<String> chat(@UserMessage String userPrompt, @V("systemPrompt") String systemPrompt);
}
// AI_GENERATE_END -
