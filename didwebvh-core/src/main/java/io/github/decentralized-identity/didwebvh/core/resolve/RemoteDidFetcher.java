package io.github.decentralizedidentity.didwebvh.core.resolve;

interface RemoteDidFetcher {

    String fetchDidLog(String httpsUrl);

    String fetchWitnessProofs(String witnessUrl);
}
