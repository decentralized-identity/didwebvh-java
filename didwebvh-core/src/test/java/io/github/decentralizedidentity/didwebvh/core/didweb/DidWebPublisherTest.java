package io.github.decentralizedidentity.didwebvh.core.didweb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DidWebPublisherTest {

    private static final String SCID = "QmfGEUAcMpzo25kF2Rhn8L2qMSAzMGeyq8kJEMabAxQzK3";
    private static final String DID_WEBVH = "did:webvh:" + SCID + ":example.com";
    private static final String DID_WEBVH_PATH =
            "did:webvh:" + SCID + ":example.com:dids:issuer";
    private static final String DID_WEB = "did:web:example.com";
    private static final String DID_WEB_PATH = "did:web:example.com:dids:issuer";

    @Test
    void convertsIdAndControllerFromWebvhToWeb() {
        DidDocument rr = resolvedResult(baseDoc(DID_WEBVH));

        DidDocument result = DidWebPublisher.toDidWeb(rr);

        assertThat(result.getId()).isEqualTo(DID_WEB);
        assertThat(result.asJsonObject().get("controller").getAsString())
                .isEqualTo(DID_WEB);
    }

    @Test
    void rewritesVerificationMethodReferences() {
        JsonObject doc = baseDoc(DID_WEBVH);
        JsonArray vms = new JsonArray();
        JsonObject vm = new JsonObject();
        vm.addProperty("id", DID_WEBVH + "#key-1");
        vm.addProperty("type", "Multikey");
        vm.addProperty("controller", DID_WEBVH);
        vm.addProperty("publicKeyMultibase", "z6MkhaXgBZDvotDkL5257faiztiGi");
        vms.add(vm);
        doc.add("verificationMethod", vms);

        DidDocument result = DidWebPublisher.toDidWeb(new DidDocument(doc));

        JsonObject outVm = result.asJsonObject()
                .getAsJsonArray("verificationMethod").get(0).getAsJsonObject();
        assertThat(outVm.get("id").getAsString()).isEqualTo(DID_WEB + "#key-1");
        assertThat(outVm.get("controller").getAsString()).isEqualTo(DID_WEB);
    }

    @Test
    void addsImplicitFilesAndWhoisServicesWhenAbsent() {
        DidDocument rr = resolvedResult(baseDoc(DID_WEBVH));

        DidDocument result = DidWebPublisher.toDidWeb(rr);

        JsonArray services = result.asJsonObject().getAsJsonArray("service");
        assertThat(services).hasSize(2);

        JsonObject files = findServiceById(services, DID_WEB + "#files");
        assertThat(files).isNotNull();
        assertThat(files.get("type").getAsString()).isEqualTo("relativeRef");
        assertThat(files.get("serviceEndpoint").getAsString())
                .isEqualTo("https://example.com/");

        JsonObject whois = findServiceById(services, DID_WEB + "#whois");
        assertThat(whois).isNotNull();
        assertThat(whois.get("type").getAsString()).isEqualTo("LinkedVerifiablePresentation");
        assertThat(whois.get("@context").getAsString())
                .isEqualTo("https://identity.foundation/linked-vp/contexts/v1");
        assertThat(whois.get("serviceEndpoint").getAsString())
                .isEqualTo("https://example.com/whois.vp");
    }

    @Test
    void servicesEndpointsUsePathAndPortFromDomain() {
        String did = "did:webvh:" + SCID + ":example.com%3A3000:dids:issuer";
        JsonObject doc = baseDoc(did);

        DidDocument result = DidWebPublisher.toDidWeb(new DidDocument(doc));

        JsonArray services = result.asJsonObject().getAsJsonArray("service");
        JsonObject files = findServiceById(services, "did:web:example.com%3A3000:dids:issuer#files");
        assertThat(files.get("serviceEndpoint").getAsString())
                .isEqualTo("https://example.com:3000/dids/issuer/");
        JsonObject whois = findServiceById(services, "did:web:example.com%3A3000:dids:issuer#whois");
        assertThat(whois.get("serviceEndpoint").getAsString())
                .isEqualTo("https://example.com:3000/dids/issuer/whois.vp");
    }

    @Test
    void doesNotOverrideExplicitFilesOrWhoisService() {
        JsonObject doc = baseDoc(DID_WEBVH);
        JsonArray services = new JsonArray();
        JsonObject customFiles = new JsonObject();
        customFiles.addProperty("id", "#files");
        customFiles.addProperty("type", "relativeRef");
        customFiles.addProperty("serviceEndpoint", "https://files.example.com/custom/");
        services.add(customFiles);
        JsonObject customWhois = new JsonObject();
        customWhois.addProperty("id", DID_WEBVH + "#whois");
        customWhois.addProperty("type", "LinkedVerifiablePresentation");
        customWhois.addProperty("serviceEndpoint", "https://whois.example.com/custom.vp");
        services.add(customWhois);
        doc.add("service", services);

        DidDocument result = DidWebPublisher.toDidWeb(new DidDocument(doc));

        JsonArray out = result.asJsonObject().getAsJsonArray("service");
        assertThat(out).hasSize(2);
        JsonObject files = findServiceById(out, "#files");
        assertThat(files.get("serviceEndpoint").getAsString())
                .isEqualTo("https://files.example.com/custom/");
        JsonObject whois = findServiceById(out, DID_WEB + "#whois");
        assertThat(whois.get("serviceEndpoint").getAsString())
                .isEqualTo("https://whois.example.com/custom.vp");
    }

    @Test
    void addsDidWebVhToAlsoKnownAs() {
        DidDocument rr = resolvedResult(baseDoc(DID_WEBVH));

        DidDocument result = DidWebPublisher.toDidWeb(rr);

        JsonArray aka = result.asJsonObject().getAsJsonArray("alsoKnownAs");
        assertThat(aka).hasSize(1);
        assertThat(aka.get(0).getAsString()).isEqualTo(DID_WEBVH);
    }

    @Test
    void preservesExistingAlsoKnownAsEntriesAndAddsWebvh() {
        JsonObject doc = baseDoc(DID_WEBVH);
        JsonArray aka = new JsonArray();
        aka.add("did:example:alt");
        doc.add("alsoKnownAs", aka);

        DidDocument result = DidWebPublisher.toDidWeb(new DidDocument(doc));

        JsonArray out = result.asJsonObject().getAsJsonArray("alsoKnownAs");
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getAsString()).isEqualTo("did:example:alt");
        assertThat(out.get(1).getAsString()).isEqualTo(DID_WEBVH);
    }

    @Test
    void deduplicatesAlsoKnownAsAndRemovesSelfDidWeb() {
        JsonObject doc = baseDoc(DID_WEBVH);
        JsonArray aka = new JsonArray();
        aka.add(DID_WEBVH);
        aka.add(DID_WEB);
        aka.add("did:example:alt");
        aka.add("did:example:alt");
        doc.add("alsoKnownAs", aka);

        DidDocument result = DidWebPublisher.toDidWeb(new DidDocument(doc));

        JsonArray out = result.asJsonObject().getAsJsonArray("alsoKnownAs");
        assertThat(out).hasSize(2);
        assertThat(out.get(0).getAsString()).isEqualTo("did:example:alt");
        assertThat(out.get(1).getAsString()).isEqualTo(DID_WEBVH);
    }

    @Test
    void toDidWebUrlDelegatesToTransformer() {
        assertThat(DidWebPublisher.toDidWebUrl(DID_WEBVH)).isEqualTo(DID_WEB);
        assertThat(DidWebPublisher.toDidWebUrl(DID_WEBVH_PATH))
                .isEqualTo(DID_WEB_PATH);
    }

    @Test
    void throwsOnNullDocument() {
        assertThatThrownBy(() -> DidWebPublisher.toDidWeb(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void throwsOnMissingId() {
        DidDocument empty = new DidDocument(new JsonObject());
        assertThatThrownBy(() -> DidWebPublisher.toDidWeb(empty))
                .isInstanceOf(ValidationException.class);
    }

    // ---- helpers ----

    private static JsonObject baseDoc(String did) {
        JsonObject doc = new JsonObject();
        doc.addProperty("@context", "https://www.w3.org/ns/did/v1");
        doc.addProperty("id", did);
        doc.addProperty("controller", did);
        return doc;
    }

    private static DidDocument resolvedResult(JsonObject doc) {
        return new DidDocument(doc);
    }

    private static JsonObject findServiceById(JsonArray services, String id) {
        for (JsonElement el : services) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonElement idEl = el.getAsJsonObject().get("id");
            if (idEl != null && id.equals(idEl.getAsString())) {
                return el.getAsJsonObject();
            }
        }
        return null;
    }
}
