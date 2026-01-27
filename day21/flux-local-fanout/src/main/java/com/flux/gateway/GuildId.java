package com.flux.gateway;

public record GuildId(String value) {
    @Override
    public String toString() {
        return value;
    }
}
