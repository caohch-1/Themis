package io.themis.core.report;

import io.themis.core.io.JsonIo;
import io.themis.core.model.BugReport;
import io.themis.core.model.FuzzOutcome;
import io.themis.core.model.GeneratedTestArtifact;
import io.themis.core.model.RpcPair;
import io.themis.core.model.ViolationTuple;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ArtifactWriter {
    private final JsonIo jsonIo;

    public ArtifactWriter(JsonIo jsonIo) {
        this.jsonIo = jsonIo;
    }

    public void writeViolations(Path outDir, List<ViolationTuple> tuples) throws IOException {
        jsonIo.write(outDir.resolve("violations.json"), tuples);
    }

    public void writeRpcPairs(Path outDir, List<RpcPair> pairs) throws IOException {
        jsonIo.write(outDir.resolve("rpc_pairs.json"), pairs);
    }

    public void writeTests(Path outDir, List<GeneratedTestArtifact> tests) throws IOException {
        jsonIo.write(outDir.resolve("tests.json"), tests);
    }

    public void writeFuzzOutcomes(Path outDir, List<FuzzOutcome> outcomes) throws IOException {
        jsonIo.write(outDir.resolve("fuzz_outcomes.json"), outcomes);
    }

    public void writeBugReports(Path outDir, List<BugReport> reports) throws IOException {
        jsonIo.write(outDir.resolve("bug_reports.json"), reports);
    }
}
