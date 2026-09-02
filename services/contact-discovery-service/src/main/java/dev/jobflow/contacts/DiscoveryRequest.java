package dev.jobflow.contacts;

import java.util.List;

record DiscoveryRequest(List<PublicSource> publicSources, List<GmailEvidence> gmailEvidence) {}
