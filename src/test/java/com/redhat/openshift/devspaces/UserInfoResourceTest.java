package com.redhat.openshift.devspaces;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserInfoResourceTest {

    @Test
    void userNameWithAtSign_emailHeaderWins() {
        // OAuth Proxy truncates X-Forwarded-User at the "@"; X-Forwarded-Email keeps the full name
        assertEquals("user1@example.com",
                UserInfoResource.resolveUserName("user1", "user1@example.com"));
    }

    @Test
    void userNameWithoutAtSign_clusterLocalSuffixStripped() {
        assertEquals("user1",
                UserInfoResource.resolveUserName("user1", "user1@cluster.local"));
    }

    @Test
    void emailHeaderMissing_fallsBackToUserHeader() {
        assertEquals("user1", UserInfoResource.resolveUserName("user1", null));
        assertEquals("user1", UserInfoResource.resolveUserName("user1", ""));
    }

    @Test
    void bothHeadersMissing_returnsNull() {
        assertNull(UserInfoResource.resolveUserName(null, null));
    }
}
