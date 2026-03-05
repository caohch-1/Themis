package io.themis.staticanalysis.publicapi;

import io.themis.core.model.AccessSite;
import soot.Body;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.tagkit.LineNumberTag;

import java.util.ArrayList;
import java.util.List;

public class SynchronizationAnalyzer {
    public boolean isProtected(List<AccessSite> accessSites) {
        if (accessSites == null || accessSites.isEmpty()) {
            return false;
        }
        for (AccessSite site : accessSites) {
            if (!isSiteProtected(site)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSiteProtected(AccessSite site) {
        if (!Scene.v().containsMethod(site.getMethodSignature())) {
            return false;
        }
        SootMethod method = Scene.v().getMethod(site.getMethodSignature());
        if (method.isSynchronized()) {
            return true;
        }
        if (!method.hasActiveBody()) {
            return false;
        }
        Body body = method.getActiveBody();
        List<Unit> units = new ArrayList<Unit>();
        for (Unit unit : body.getUnits()) {
            units.add(unit);
        }
        int targetIndex = findTargetIndex(units, site);
        if (targetIndex < 0) {
            return false;
        }
        return insideMonitorRegion(units, targetIndex);
    }

    private int findTargetIndex(List<Unit> units, AccessSite site) {
        int line = site.getLineNumber();
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (!unit.toString().equals(site.getStatement())) {
                continue;
            }
            if (line < 0) {
                return i;
            }
            LineNumberTag tag = (LineNumberTag) unit.getTag("LineNumberTag");
            if (tag != null && tag.getLineNumber() == line) {
                return i;
            }
        }
        for (int i = 0; i < units.size(); i++) {
            Unit unit = units.get(i);
            if (unit.toString().equals(site.getStatement())) {
                return i;
            }
        }
        return -1;
    }

    private boolean insideMonitorRegion(List<Unit> units, int targetIndex) {
        int monitorDepth = 0;
        for (int i = 0; i <= targetIndex; i++) {
            String text = units.get(i).toString().toLowerCase();
            if (text.contains("entermonitor")) {
                monitorDepth++;
            }
            if (text.contains("exitmonitor") && monitorDepth > 0) {
                monitorDepth--;
            }
        }
        if (monitorDepth > 0) {
            return true;
        }
        String targetText = units.get(targetIndex).toString().toLowerCase();
        return targetText.contains("lock") || targetText.contains("unlock");
    }
}
