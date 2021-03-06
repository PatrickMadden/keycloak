package org.keycloak.exportimport;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.keycloak.model.test.AbstractModelTest;
import org.keycloak.model.test.ImportTest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderSession;
import org.keycloak.provider.ProviderSessionFactory;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.managers.ApplianceBootstrap;
import org.keycloak.services.managers.RealmManager;
import org.keycloak.services.resources.KeycloakApplication;
import org.keycloak.util.ProviderLoader;

import java.util.Iterator;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
public abstract class ExportImportTestBase {

    protected ProviderSessionFactory factory;

    protected ProviderSession providerSession;
    protected KeycloakSession identitySession;
    protected RealmManager realmManager;

    @After
    public void after() {
        System.getProperties().remove("keycloak.model.provider");
    }

    @Test
    public void testExportImport() throws Exception {
        // Init JPA model
        System.setProperty("keycloak.model.provider", getExportModelProvider());
        factory = KeycloakApplication.createProviderSessionFactory();

        // Bootstrap admin realm
        beginTransaction();
        new ApplianceBootstrap().bootstrap(identitySession, "/auth");
        commitTransaction();

        // Classic import of realm to JPA model
        beginTransaction();
        RealmRepresentation rep = AbstractModelTest.loadJson("testrealm.json");
        realmManager = new RealmManager(identitySession);
        RealmModel realm = realmManager.createRealm("demo", rep.getRealm());
        realmManager.importRealm(rep, realm);

        commitTransaction();

        // Full export of realm
        exportModel(factory);

        beginTransaction();
        realm = identitySession.getRealm("demo");
        String wburkeId = realm.getUser("wburke").getId();
        String appId = realm.getApplicationByName("Application").getId();

        // Commit transaction and close JPA now
        commitTransaction();
        factory.close();

        // Bootstrap mongo session and factory
        System.setProperty("keycloak.model.provider", getImportModelProvider());
        factory = KeycloakApplication.createProviderSessionFactory();

        // Full import of previous export into mongo
        importModel(factory);

        // Verify it's imported in mongo (reusing ImportTest)
        beginTransaction();
        RealmModel importedRealm = identitySession.getRealm("demo");
        System.out.println("Exported realm: " + realm + ", Imported realm: " + importedRealm);

        Assert.assertEquals(wburkeId, importedRealm.getUser("wburke").getId());
        Assert.assertEquals(appId, importedRealm.getApplicationByName("Application").getId());
        ImportTest.assertDataImportedInRealm(importedRealm);

        // Commit and close Mongo
        commitTransaction();
        factory.close();
    }

    protected abstract String getExportModelProvider();

    protected abstract String getImportModelProvider();

    protected abstract void exportModel(ProviderSessionFactory factory);

    protected abstract void importModel(ProviderSessionFactory factory);

    protected void beginTransaction() {
        providerSession = factory.createSession();
        identitySession = providerSession.getProvider(KeycloakSession.class);
        identitySession.getTransaction().begin();
        realmManager = new RealmManager(identitySession);
    }

    protected void commitTransaction() {
        identitySession.getTransaction().commit();
        providerSession.close();
    }

    protected ExportImportProvider getExportImportProvider() {
        Iterator<ExportImportProvider> providers = ProviderLoader.load(ExportImportProvider.class).iterator();

        if (providers.hasNext()) {
            return providers.next();
        } else {
            throw new IllegalStateException("ExportImportProvider not found");
        }
    }
}
