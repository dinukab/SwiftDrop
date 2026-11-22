package com.swiftdrop.dispatch.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.dispatch.domain.DispatcherHub;
import com.swiftdrop.dispatch.domain.DispatcherHubRepository;
import com.swiftdrop.dispatch.domain.Hub;
import com.swiftdrop.dispatch.domain.HubRepository;
import com.swiftdrop.identity.api.UserDirectory;
import com.swiftdrop.identity.api.UserSummary;

/**
 * Hub reference data and dispatcher-to-hub bindings. The hub of a dispatcher always comes from
 * this binding, never from a value sent by the browser.
 */
@Service
@Lazy
@Transactional(readOnly = true)
public class HubService {

    private static final Logger log = LoggerFactory.getLogger(HubService.class);
    private static final String DISPATCHER_ROLE = "DISPATCHER";

    private final HubRepository hubs;
    private final DispatcherHubRepository bindings;
    private final UserDirectory users;
    private final Clock clock;

    @Autowired
    public HubService(HubRepository hubs, DispatcherHubRepository bindings, UserDirectory users) {
        this(hubs, bindings, users, Clock.systemUTC());
    }

    HubService(HubRepository hubs, DispatcherHubRepository bindings, UserDirectory users, Clock clock) {
        this.hubs = Objects.requireNonNull(hubs, "hubs");
        this.bindings = Objects.requireNonNull(bindings, "bindings");
        this.users = Objects.requireNonNull(users, "users");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Active hubs, Main Hub first. */
    public List<Hub> listHubs() {
        return hubs.findByActiveTrueOrderByMainDescNameAsc();
    }

    /** The hub the dispatcher is bound to, empty if none (or if the hub was deactivated). */
    public Optional<Hub> hubForDispatcher(UUID dispatcherUserId) {
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId must not be null");
        return bindings.findById(dispatcherUserId)
                .flatMap(binding -> hubs.findById(binding.getHubId()))
                .filter(Hub::isActive);
    }

    /**
     * Every dispatcher that has a hub, with that hub, for the admin screen. Dispatchers without a binding,
     * or whose hub was deactivated, are absent.
     */
    public Map<UUID, Hub> listDispatcherHubs() {
        Map<UUID, Hub> hubsById = new HashMap<>();
        for (Hub hub : hubs.findAll()) {
            hubsById.put(hub.getId(), hub);
        }
        Map<UUID, Hub> result = new LinkedHashMap<>();
        for (DispatcherHub binding : bindings.findAll()) {
            Hub hub = hubsById.get(binding.getHubId());
            if (hub != null && hub.isActive() && users.findUser(binding.getUserId()).isPresent()) {
                result.put(binding.getUserId(), hub);
            }
        }
        return result;
    }

    /** The active hub of a province code such as {@code LK-UP}, empty if there is none. */
    public Optional<Hub> findActiveByCode(String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return Optional.empty();
        }
        return hubs.findByCode(provinceCode.trim().toUpperCase(Locale.ROOT)).filter(Hub::isActive);
    }

    /** The Main Hub (Colombo), empty if none is active. */
    public Optional<Hub> findMainHub() {
        return hubs.findByMainTrueAndActiveTrue();
    }

    /** Throws if hub not found. */
    public Hub requireHub(UUID hubId) {
        Objects.requireNonNull(hubId, "hubId must not be null");
        return hubs.findById(hubId).orElseThrow(HubNotFoundException::new);
    }

    /**
     * Binds an enabled dispatcher to an active hub, or moves them if already bound.
     *
     * @throws DispatcherUserNotFoundException if no user has {@code dispatcherUserId}
     * @throws DispatcherNotEligibleException if the account is disabled or not a {@code DISPATCHER}
     * @throws HubNotFoundException if {@code hubCode} is unknown or inactive
     */
    @Transactional
    public Hub assignDispatcher(UUID dispatcherUserId, String hubCode) {
        Objects.requireNonNull(dispatcherUserId, "dispatcherUserId must not be null");
        Objects.requireNonNull(hubCode, "hubCode must not be null");

        UserSummary user = users.findUser(dispatcherUserId).orElseThrow(DispatcherUserNotFoundException::new);
        if (!user.enabled()) {
            throw new DispatcherNotEligibleException("Dispatcher account is disabled");
        }
        if (!user.hasRole(DISPATCHER_ROLE)) {
            throw new DispatcherNotEligibleException("User does not have the DISPATCHER role");
        }
        Hub hub = hubs.findByCode(hubCode.trim().toUpperCase(Locale.ROOT))
                .filter(Hub::isActive)
                .orElseThrow(HubNotFoundException::new);

        bindings.findById(dispatcherUserId).ifPresentOrElse(
                binding -> binding.moveTo(hub.getId(), clock.instant()),
                () -> bindings.save(new DispatcherHub(dispatcherUserId, hub.getId(), clock.instant())));
        // IDs and codes only: no names or emails in logs
        log.info("Dispatcher {} bound to hub {}", dispatcherUserId, hub.getCode());
        return hub;
    }
}
