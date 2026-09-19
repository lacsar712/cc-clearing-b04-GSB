package com.clearing.netting.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP-stack settle checks: JWT auth, viewer rejection, double-sided
 * validation and persistence of remark/time/operator visible on re-fetch.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SettleFlowIntegrationTest {

    private static final AtomicInteger DATE_OFFSET = new AtomicInteger(0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String createMember(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/members")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("memberId").asText();
    }

    private void createObligation(String token, String payer, String payee, String amount, LocalDate settleDate)
            throws Exception {
        String body = """
                {"payerMemberId":"%s","payeeMemberId":"%s","currency":"USD","amount":%s,
                 "tradeDate":"%s","settleDate":"%s"}"""
                .formatted(payer, payee, amount, settleDate.minusDays(1), settleDate);
        mockMvc.perform(post("/api/obligations")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    /** Creates 3 members + triangle obligations for a unique settle date and executes a COMPLETED run. */
    private String completedRun(String token) throws Exception {
        LocalDate settleDate = LocalDate.of(2026, 1, 1).plusDays(DATE_OFFSET.getAndAdd(7));
        String m1 = createMember(token, "Bank A " + UUID.randomUUID());
        String m2 = createMember(token, "Bank B " + UUID.randomUUID());
        String m3 = createMember(token, "Bank C " + UUID.randomUUID());
        createObligation(token, m1, m2, "100.00000000", settleDate);
        createObligation(token, m2, m3, "60.00000000", settleDate);
        createObligation(token, m3, m1, "40.00000000", settleDate);

        MvcResult result = mockMvc.perform(post("/api/netting-runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settleDate\":\"%s\",\"currency\":\"USD\"}".formatted(settleDate)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode run = json(result).get("run");
        assertEquals("COMPLETED", run.get("status").asText());
        return run.get("runId").asText();
    }

    /** OPEN obligations involving a suspended member make the run end FAILED. */
    private String failedRun(String token) throws Exception {
        LocalDate settleDate = LocalDate.of(2026, 1, 1).plusDays(DATE_OFFSET.getAndAdd(7));
        String m1 = createMember(token, "Bad Bank A " + UUID.randomUUID());
        String m2 = createMember(token, "Bad Bank B " + UUID.randomUUID());
        createObligation(token, m1, m2, "10.00000000", settleDate);
        mockMvc.perform(post("/api/members/" + m2 + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post("/api/netting-runs")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settleDate\":\"%s\",\"currency\":\"USD\"}".formatted(settleDate)))
                .andExpect(status().isBadRequest())
                .andReturn();
        // The run is persisted FAILED before the error propagates; find it in the list.
        MvcResult listResult = mockMvc.perform(get("/api/netting-runs")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode runs = json(listResult);
        for (JsonNode row : runs) {
            if (settleDate.toString().equals(row.get("settleDate").asText())
                    && "FAILED".equals(row.get("status").asText())) {
                return row.get("runId").asText();
            }
        }
        throw new IllegalStateException("expected FAILED run for " + settleDate
                + ", got: " + result.getResponse().getContentAsString());
    }

    private JsonNode runJson(String token, String runId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/netting-runs/" + runId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("run");
    }

    private MvcResult settle(String token, String runId, String rawJsonBody) throws Exception {
        var req = post("/api/netting-runs/" + runId + "/settle")
                .header("Authorization", bearer(token));
        if (rawJsonBody != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(rawJsonBody);
        }
        return mockMvc.perform(req).andReturn();
    }

    @Test
    void operatorSettlesCompletedRunWithRemarkAndAuditIsVisibleAfterRefresh() throws Exception {
        String operator = login("operator", "op123456");
        String runId = completedRun(operator);

        byte[] utf8Body = objectMapper.writeValueAsBytes(java.util.Map.of(
                "remark", "日终批次核对无误，确认结算"));
        mockMvc.perform(post("/api/netting-runs/" + runId + "/settle")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                        .content(utf8Body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.settleRemark").value("日终批次核对无误，确认结算"))
                .andExpect(jsonPath("$.settledBy").value("operator"))
                .andExpect(jsonPath("$.settledAt").isNotEmpty());

        // visible on detail refresh
        JsonNode detail = runJson(operator, runId);
        assertEquals("SETTLED", detail.get("status").asText());
        assertEquals("日终批次核对无误，确认结算", detail.get("settleRemark").asText());
        assertEquals("operator", detail.get("settledBy").asText());
        assertNotNull(detail.get("settledAt").asText());

        // visible in the batch list too
        MvcResult listResult = mockMvc.perform(get("/api/netting-runs")
                        .header("Authorization", bearer(operator)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode listed = json(listResult);
        JsonNode match = null;
        for (JsonNode row : listed) {
            if (row.get("runId").asText().equals(runId)) {
                match = row;
                break;
            }
        }
        assertNotNull(match, "settled run must appear in list");
        assertEquals("SETTLED", match.get("status").asText());
        assertEquals("日终批次核对无误，确认结算", match.get("settleRemark").asText());
    }

    @Test
    void blankRemarkRejectedOverHttpAndRunStaysCompleted() throws Exception {
        String operator = login("operator", "op123456");
        String runId = completedRun(operator);

        // whitespace-only remark -> bean validation 400 with field-level message
        MvcResult r1 = settle(operator, runId, "{\"remark\":\"   \"}");
        assertEquals(400, r1.getResponse().getStatus());
        assertTrue(r1.getResponse().getContentAsString().contains("remark"));

        // missing body -> 400, not a generic 500
        MvcResult r2 = settle(operator, runId, null);
        assertEquals(400, r2.getResponse().getStatus());

        assertEquals("COMPLETED", runJson(operator, runId).get("status").asText());
    }

    @Test
    void failedRunSettleRejectedOverHttp() throws Exception {
        String operator = login("operator", "op123456");
        String runId = failedRun(operator);

        MvcResult result = settle(operator, runId, "{\"remark\":\"try to settle failed\"}");
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("INVALID_STATE"));
        assertEquals("FAILED", runJson(operator, runId).get("status").asText());
    }

    @Test
    void alreadySettledRunCannotSettleAgain() throws Exception {
        String operator = login("operator", "op123456");
        String runId = completedRun(operator);
        settle(operator, runId, "{\"remark\":\"first close\"}");

        MvcResult result = settle(operator, runId, "{\"remark\":\"again\"}");
        assertEquals(400, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("ALREADY_SETTLED"));
        assertEquals("first close", runJson(operator, runId).get("settleRemark").asText());
    }

    @Test
    void viewerHasNoSettleAccessEvenWhenCallingApiDirectly() throws Exception {
        String operator = login("operator", "op123456");
        String viewer = login("viewer", "view123456");
        String runId = completedRun(operator);

        MvcResult result = settle(viewer, runId, "{\"remark\":\"viewer tries\"}");
        assertEquals(403, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("FORBIDDEN"));
        assertEquals("COMPLETED", runJson(operator, runId).get("status").asText());
    }

    @Test
    void unauthenticatedSettleRejected() throws Exception {
        String operator = login("operator", "op123456");
        String runId = completedRun(operator);

        mockMvc.perform(post("/api/netting-runs/" + runId + "/settle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"remark\":\"no token\"}"))
                .andExpect(status().isUnauthorized());
    }
}
