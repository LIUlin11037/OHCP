package com.ohcp.cropagent.guardrail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("事实校验：编造的用量必须被拦下")
class FactValidatorTest {

    private final FactValidator validator = new FactValidator();

    @Test
    @DisplayName("用量来自知识库 -> 放行")
    void passWhenFromKnowledge() {
        FactValidator.Result r = validator.validate(
                "尿素用量 5~8 kg/亩，农家肥 1000~1500 kg/亩",
                "{\"chemicalPlan\":[{\"dosage\":\"5~8 kg/亩\"}],\"organicPlan\":[{\"dosage\":\"1000~1500 kg/亩\"}]}");
        assertTrue(r.passed(), "知识库里的数值不该被拦：" + r.unsupported());
    }

    @Test
    @DisplayName("模型编出 20 kg/亩 -> 拦下并标出")
    void rejectFabricatedDosage() {
        FactValidator.Result r = validator.validate(
                "建议追施尿素 20 kg/亩",
                "{\"chemicalPlan\":[{\"dosage\":\"5~8 kg/亩\"}]}");
        assertFalse(r.passed(), "20 kg/亩 不在证据里，必须被拦");
        assertTrue(r.unsupported().toString().contains("20"), "应报告未溯源数值：" + r.unsupported());
    }

    @Test
    @DisplayName("没有数值的回答 -> 直接放行")
    void passWhenNoQuantity() {
        assertTrue(validator.validate("建议增施有机肥，改善土壤结构", "任意证据").passed());
    }
}
