package com.swiftdrop.dispatch.web;

import com.swiftdrop.dispatch.domain.Hub;

/** A provincial hub; {@code code} is the province code, for example {@code LK-UP}. */
record HubResponse(String code, String name, boolean main) {

    static HubResponse from(Hub hub) {
        return new HubResponse(hub.getCode(), hub.getName(), hub.isMain());
    }
}
