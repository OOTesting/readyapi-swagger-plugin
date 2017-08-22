package com.smartbear.swagger.assertion

import com.eviware.soapui.impl.wsdl.teststeps.RestTestRequestStep
import com.eviware.soapui.security.assertion.ValidHttpStatusCodesAssertion
import v2.io.swagger.models.Response

import static com.eviware.soapui.security.assertion.ValidHttpStatusCodesAssertion.LABEL


class ValidStatusCodesAssertionCreator implements AssertionCreator {
    @Override
    void createAssertion(RestTestRequestStep restTestRequestStep, Map<String, Response> responseMap,
                         Map<String, Object> context) {
        if (!responseMap.containsKey('default')) {
            ValidHttpStatusCodesAssertion assertion = (ValidHttpStatusCodesAssertion) restTestRequestStep.addAssertion(LABEL)
            assertion.codes = responseMap.keySet().join(',')
        }
    }
}
