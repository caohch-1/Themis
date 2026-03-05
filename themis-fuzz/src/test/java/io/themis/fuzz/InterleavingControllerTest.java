package io.themis.fuzz;

import io.themis.fuzz.interleaving.SignalWaitController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InterleavingControllerTest {
    @Test
    public void signalWaitPermitsOrderControl() throws Exception {
        SignalWaitController controller = new SignalWaitController();
        controller.registerPoint("p1");
        Thread t = new Thread(() -> {
            try {
                controller.awaitBefore("p1");
                controller.awaitAfter("p1");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();
        controller.signalBefore("p1");
        controller.signalAfter("p1");
        t.join(1000);
        Assertions.assertFalse(t.isAlive());
    }
}
