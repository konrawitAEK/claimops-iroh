package com.chubb.claimops.claim.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chubb.claimops.claim.ClaimService;
import com.chubb.claimops.claim.domain.Claim;
import com.chubb.claimops.common.auth.CurrentUserArgumentResolver;
import com.chubb.claimops.common.auth.HeaderAuthInterceptor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer test: real interceptor, argument resolver and exception handler, with a mocked service. Shows how
 * failures from the layers below reach the client as HTTP status codes. No database or Kafka is needed.
 */
@WebMvcTest(StaffClaimController.class)
@Import({HeaderAuthInterceptor.class, CurrentUserArgumentResolver.class})
class StaffClaimControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ClaimService claimService;

    @Test
    void anOptimisticLockFailureOnPickupBecomes409ProblemDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(claimService.pickUp(any(), eq(id))).thenThrow(new ObjectOptimisticLockingFailureException(Claim.class, id));

        mvc.perform(post("/api/staff/claims/{id}/pickup", id)
                        .header("X-User-Role", "STAFF")
                        .header("X-User-Name", "carol"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Concurrent update"))
                .andExpect(jsonPath("$.status").value(409));
    }



    @Test
    void aClaimantCallingAStaffEndpointBecomes403() throws Exception {
        mvc.perform(post("/api/staff/claims/{id}/pickup", UUID.randomUUID())
                        .header("X-User-Role", "CLAIMANT")
                        .header("X-User-Name", "alice"))
                .andExpect(status().isForbidden());
    }

}
