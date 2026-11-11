package com.redhat.openshift.devspaces;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

@Path("/api/user")
public class UserInfoResource {

    @Inject
    GroupMappingService groupMappingService;

    @Inject
    OpenShiftGroupService openShiftGroupService;

    /**
     * Suffix appended by the OpenShift OAuth Proxy to X-Forwarded-Email when the
     * OpenShift user name does not contain an "@".
     */
    static final String CLUSTER_LOCAL_SUFFIX = "@cluster.local";

    /**
     * Resolve the OpenShift user name from the OAuth Proxy headers.
     * <p>
     * The OAuth Proxy sets X-Forwarded-Email to the OpenShift user name (plus
     * "@cluster.local" when the name has no "@") and X-Forwarded-User to the part
     * before the "@". For user names that contain an "@" (for example when the
     * identity provider maps the user name to an e-mail address) X-Forwarded-User
     * is truncated and no longer matches the OpenShift group membership, so the
     * e-mail header is preferred.
     *
     * @param userHeader  value of X-Forwarded-User, may be null
     * @param emailHeader value of X-Forwarded-Email, may be null
     * @return the OpenShift user name, or null when both headers are absent
     */
    static String resolveUserName(String userHeader, String emailHeader) {
        if (emailHeader != null && !emailHeader.isEmpty()) {
            if (emailHeader.endsWith(CLUSTER_LOCAL_SUFFIX)) {
                return emailHeader.substring(0, emailHeader.length() - CLUSTER_LOCAL_SUFFIX.length());
            }
            return emailHeader;
        }
        return userHeader;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserInfo(@Context HttpHeaders headers) {
        String userHeader = resolveUserName(
                headers.getHeaderString("X-Forwarded-User"),
                headers.getHeaderString("X-Forwarded-Email"));
        String groupsHeader = headers.getHeaderString("X-Forwarded-Groups");

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode userInfo = mapper.createObjectNode();
        userInfo.put("user", userHeader);
        userInfo.put("groups", groupsHeader);

        // Check which OpenShift groups the user belongs to and match with ConfigMap
        if (userHeader != null && !userHeader.isEmpty()) {
            List<String> userGroups = openShiftGroupService.getUserGroups(userHeader);
            ArrayNode userGroupsArray = mapper.createArrayNode();
            for (String group : userGroups) {
                userGroupsArray.add(group);
            }
            userInfo.set("userOpenShiftGroups", userGroupsArray);

            // Find matching Dev Spaces URLs using AND logic for comma-separated group keys
            ArrayNode devSpacesMappings = mapper.createArrayNode();
            Map<String, String> matchingMappings = groupMappingService.getMatchingMappings(userGroups);
            for (Map.Entry<String, String> entry : matchingMappings.entrySet()) {
                ObjectNode mapping = mapper.createObjectNode();
                mapping.put("groupKey", entry.getKey());
                mapping.put("devSpacesUrl", entry.getValue());
                devSpacesMappings.add(mapping);
            }
            userInfo.set("devSpacesMappings", devSpacesMappings);
        }

        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        cacheControl.setNoStore(true);
        cacheControl.setMustRevalidate(true);

        return Response.ok(userInfo)
                .cacheControl(cacheControl)
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build();
    }
}