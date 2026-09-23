package com.collabmodeler.api.ai;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.collabmodeler.api.diagram.DiagramOperationRequest;
import com.collabmodeler.api.diagram.DiagramService;
import com.collabmodeler.api.support.ConflictException;
import com.collabmodeler.api.support.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class AssistantCommandService {
    private static final Set<String> DESTRUCTIVE = Set.of("CLASS_DELETED", "ATTRIBUTE_DELETED", "ASSOCIATION_DELETED", "ENUMERATION_DELETED", "ENUMERATION_VALUE_DELETED", "GENERALIZATION_DELETED", "PACKAGE_DELETED", "MODEL_RESTORED");
    private final DiagramService diagrams; private final LocalCommandParser local; private final List<TextCommandProvider> providers;
    private final AssistantProposalRepository proposals; private final AiProperties properties; private final ObjectMapper mapper;

    public AssistantCommandService(DiagramService diagrams, LocalCommandParser local, List<TextCommandProvider> providers,
                                   AssistantProposalRepository proposals, AiProperties properties, ObjectMapper mapper) {
        this.diagrams = diagrams; this.local = local; this.providers = providers; this.proposals = proposals;
        this.properties = properties; this.mapper = mapper;
    }

    @Transactional
    public Proposal interpret(UUID diagramId, String instruction, String subject) {
        String clean = instruction == null ? "" : instruction.trim();
        if (clean.isEmpty() || clean.length() > properties.getMaxInstructionLength()) throw new IllegalArgumentException("La instrucción debe tener entre 1 y " + properties.getMaxInstructionLength() + " caracteres");
        DiagramDocument current = diagrams.get(diagramId);
        var localOperation = local.parse(clean, current);
        TextCommandProvider remoteProvider = localOperation.isEmpty() ? configuredProvider() : null;
        DiagramOperationRequest operation = localOperation.orElseGet(() -> remoteProvider.interpret(clean, current));
        String provider = localOperation.isPresent() ? "local-deterministic" : remoteProvider.id();
        DiagramDocument preview = diagrams.preview(diagramId, operation);
        boolean confirmation = isDestructive(operation) || operationCount(operation) > 5;
        UUID proposalId = UUID.randomUUID();
        proposals.save(new AssistantProposalEntity(proposalId, diagramId, subject, provider, sha256(clean), write(operation), confirmation,
            Instant.now().plus(Math.max(1, properties.getProposalMinutes()), ChronoUnit.MINUTES)));
        return new Proposal(proposalId, provider, confirmation, operation, preview, summarize(operation));
    }

    @Transactional
    public AppliedProposal apply(UUID diagramId, UUID proposalId, String subject, String authorName, boolean confirmed) {
        AssistantProposalEntity proposal = proposals.findForUpdate(proposalId).orElseThrow(() -> new NotFoundException("Propuesta no encontrada"));
        if (!proposal.getDiagramId().equals(diagramId) || !proposal.getAuthorSubject().equals(subject)) throw new NotFoundException("Propuesta no encontrada");
        if (proposal.getAppliedAt() != null) throw new ConflictException("PROPOSAL_ALREADY_APPLIED", "La propuesta ya fue aplicada", null, null, null);
        if (proposal.getExpiresAt().isBefore(Instant.now())) throw new ConflictException("PROPOSAL_EXPIRED", "La propuesta expiró; vuelve a interpretar la instrucción", null, null, null);
        if (proposal.isRequiresConfirmation() && !confirmed) throw new IllegalArgumentException("La propuesta requiere confirmación explícita");
        DiagramOperationRequest operation = read(proposal.getOperationJson());
        DiagramDocument result = diagrams.apply(diagramId, operation, subject, authorName, "ASSISTANT", proposal.getProvider());
        proposal.markApplied();
        return new AppliedProposal(operation, result, proposal.getProvider());
    }

    private TextCommandProvider configuredProvider() {
        String configured = properties.getProvider();
        return providers.stream().filter(value -> value.id().equals(configured) || value.id().startsWith(configured + ":")).findFirst()
            .orElseThrow(() -> new AiUnavailableException("Proveedor de IA no disponible: " + configured));
    }
    private boolean isDestructive(DiagramOperationRequest operation) {
        if (DESTRUCTIVE.contains(operation.type())) return true;
        if (!"BATCH".equals(operation.type())) return false;
        for (JsonNode child : operation.payload().path("operations")) if (isDestructive(mapper.convertValue(child, DiagramOperationRequest.class))) return true;
        return false;
    }
    private int operationCount(DiagramOperationRequest operation) {
        if (!"BATCH".equals(operation.type())) return 1;
        int count = 0; for (JsonNode child : operation.payload().path("operations")) count += operationCount(mapper.convertValue(child, DiagramOperationRequest.class)); return count;
    }
    private String summarize(DiagramOperationRequest operation) { int count = operationCount(operation); return count == 1 ? operation.type() : count + " operaciones atómicas"; }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }
    private String write(DiagramOperationRequest value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("No se pudo guardar la propuesta", exception); } }
    private DiagramOperationRequest read(String value) { try { return mapper.readValue(value, DiagramOperationRequest.class); } catch (Exception exception) { throw new IllegalStateException("La propuesta almacenada es inválida", exception); } }

    public record Proposal(UUID proposalId, String provider, boolean requiresConfirmation, DiagramOperationRequest operation,
                           DiagramDocument previewDiagram, String summary) {}
    public record AppliedProposal(DiagramOperationRequest operation, DiagramDocument diagram, String provider) {}
}
