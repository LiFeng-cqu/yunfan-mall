package com.yunfan.common.ai;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一个 function-calling 工具的描述（转成 OpenAI 兼容的 tools 数组项）。
 */
@Data
public class ToolSpec {

    private String name;

    private String description;

    /**
     * JSON Schema（type=object，properties，required...）。
     */
    private Map<String, Object> parameters;

    public ToolSpec() {
    }

    public ToolSpec(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    /**
     * 转换为 tools 数组里的单个元素。
     */
    public Map<String, Object> toJson() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters == null ? emptyParams() : parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private static Map<String, Object> emptyParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        return params;
    }

    public static ToolSpec of(String name, String description, Map<String, Object> parameters) {
        return new ToolSpec(name, description, parameters);
    }
}
