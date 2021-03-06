package org.keycloak.audit.email;

import org.jboss.logging.Logger;
import org.keycloak.audit.AuditListener;
import org.keycloak.audit.Event;
import org.keycloak.audit.EventType;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class EmailAuditListener implements AuditListener {

    private static final Logger log = Logger.getLogger(EmailAuditListener.class);

    private KeycloakSession keycloakSession;
    private EmailProvider emailProvider;
    private Set<EventType> includedEvents;

    public EmailAuditListener(KeycloakSession keycloakSession, EmailProvider emailProvider, Set<EventType> includedEvents) {
        this.keycloakSession = keycloakSession;
        this.emailProvider = emailProvider;
        this.includedEvents = includedEvents;
    }

    @Override
    public void onEvent(Event event) {
        if (includedEvents.contains(event.getEvent())) {
            if (event.getRealmId() != null && event.getUserId() != null) {
                RealmModel realm = keycloakSession.getRealm(event.getRealmId());
                UserModel user = realm.getUserById(event.getUserId());
                if (user != null && user.getEmail() != null && user.isEmailVerified()) {
                    try {
                        emailProvider.setRealm(realm).setUser(user).sendEvent(event);
                    } catch (EmailException e) {
                        log.error("Failed to send event mail", e);
                    }
                }
            }
        }
    }

    @Override
    public void close() {
    }

}
