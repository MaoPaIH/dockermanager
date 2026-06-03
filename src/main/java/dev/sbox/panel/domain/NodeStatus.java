package dev.sbox.panel.domain;

public enum NodeStatus {
    CREATING("创建中"),
    RUNNING("运行中"),
    STOPPED("已停止"),
    EXPIRED("已到期"),
    TRAFFIC_LIMITED("流量超限"),
    ERROR("异常"),
    DELETED("已删除");

    private final String label;

    NodeStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
