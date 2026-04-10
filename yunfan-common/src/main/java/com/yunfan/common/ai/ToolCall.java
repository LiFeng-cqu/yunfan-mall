package com.yunfan.common.ai;

import lombok.Data;

/**
 * 模型返回的一次工具调用（function calling）。
 */
@Data
public class ToolCall {

    /**
     * 工具调用 id，执行结果回填时必须原样带回去。
     */
    private String id;

    /**
     * 工具名。
     */
    private String functionName;

    /**
     * 参数，JSON 字符串。
     */
    private String arguments;
}
