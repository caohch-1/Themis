package io.themis.cli;

import io.themis.core.io.JsonIo;
import io.themis.core.model.BugReport;
import io.themis.core.model.FuzzOutcome;
import io.themis.core.model.FuzzStage;
import io.themis.core.model.SeedPoolState;
import io.themis.core.model.SymptomCandidate;
import io.themis.core.model.InterleavingShape;
import io.themis.core.model.ParameterSeed;
import io.themis.core.report.BugReportBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PipelineSmokeTest {
    @Test
    public void artifactFlowBuildsBugReports() throws Exception {
        SeedPoolState pool = new SeedPoolState("v1", FuzzStage.STAGE_III, 1, Arrays.asList(new ParameterSeed("s", Collections.singletonMap("p", "1"), 1.0, "x")), 0);
        FuzzOutcome outcome = new FuzzOutcome("v1", true, FuzzStage.STAGE_III, Arrays.asList("t1"), Arrays.asList(InterleavingShape.ORDER_FORWARD), Arrays.asList(new SymptomCandidate("c", io.themis.core.model.SymptomPattern.MR, io.themis.core.model.SymptomKind.NULL_DEREFERENCE, "stmt", Arrays.asList("m"), 0)), pool, Collections.singletonMap("k", "v"));
        List<BugReport> reports = new BugReportBuilder().build(Arrays.asList(outcome));
        Assertions.assertEquals(1, reports.size());

        Path out = Files.createTempDirectory("themis-smoke").resolve("bug_reports.json");
        new JsonIo().write(out, reports);
        Assertions.assertTrue(Files.exists(out));
    }
}
