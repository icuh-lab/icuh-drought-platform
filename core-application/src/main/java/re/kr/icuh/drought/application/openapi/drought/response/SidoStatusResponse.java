package re.kr.icuh.drought.application.openapi.drought.response;

import re.kr.icuh.drought.persistence.openapi.drought.entity.DroughtMonthlyReportSidoStatus;

public record SidoStatusResponse(String sido, boolean detected, String maxGrade) {
    public static SidoStatusResponse of(DroughtMonthlyReportSidoStatus entity) {
        String grade = entity.getMaxGrade() == null ? null : entity.getMaxGrade().name();
        return new SidoStatusResponse(entity.getSido(), entity.isDetected(), grade);
    }
}
