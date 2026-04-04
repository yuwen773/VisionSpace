package com.yuwen.visionspace.agent.service;

import java.util.List;

public interface UserMcpPreferenceService {

    List<String> getDefaultEnabledServers(Long userId);

    void setDefaultEnabledServers(Long userId, List<String> mcpServerCodes);
}
