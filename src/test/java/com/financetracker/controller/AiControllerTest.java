package com.financetracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financetracker.service.AiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full-context integration test (same style as TransactionControllerTest): real
// routing/validation/security, with only AiService faked so we test the WEB layer.
@SpringBootTest
@AutoConfigureMockMvc
class AiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AiService aiService;

    // Controller does UUID.fromString(principal.getName()), so the username must be a UUID.
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Test
    @WithMockUser(username = USER_ID)
    void spendingSummary_authenticated_returns200() throws Exception {
        when(aiService.spendingSummary(any())).thenReturn("You spent $340 on food this month.");

        mockMvc.perform(get("/api/ai/spending-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("You spent $340 on food this month."));
    }

    @Test
    void spendingSummary_noAuth_isRejected() throws Exception {
        mockMvc.perform(get("/api/ai/spending-summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = USER_ID)
    void categorize_validBody_returns200() throws Exception {
        when(aiService.categorize(anyString())).thenReturn("Entertainment");

        String body = objectMapper.writeValueAsString(Map.of("description", "Netflix 15.99"));

        mockMvc.perform(post("/api/ai/categorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Entertainment"));
    }

    @Test
    @WithMockUser(username = USER_ID)
    void categorize_blankDescription_returns400() throws Exception {
        // @NotBlank on the DTO fails binding before the controller body runs.
        String body = objectMapper.writeValueAsString(Map.of("description", ""));

        mockMvc.perform(post("/api/ai/categorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = USER_ID)
    void budgetAdvice_authenticated_returns200() throws Exception {
        when(aiService.budgetAdvice(any())).thenReturn("You could save $200/month by reducing dining out.");

        mockMvc.perform(get("/api/ai/budget-advice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advice").value("You could save $200/month by reducing dining out."));
    }
}
