package com.payline.payment.amazonv2.service.impl;

import com.payline.payment.amazonv2.MockUtils;
import com.payline.payment.amazonv2.bean.CheckoutSession;
import com.payline.payment.amazonv2.bean.nested.Buyer;
import com.payline.payment.amazonv2.utils.amazon.ClientUtils;
import com.payline.payment.amazonv2.utils.constant.RequestContextKeys;
import com.payline.payment.amazonv2.utils.form.FormUtils;
import com.payline.pmapi.bean.payment.RequestContext;
import com.payline.pmapi.bean.paymentform.bean.field.PaymentFormDisplayFieldText;
import com.payline.pmapi.bean.paymentform.bean.field.PaymentFormField;
import com.payline.pmapi.bean.paymentform.bean.form.CustomForm;
import com.payline.pmapi.bean.paymentform.bean.form.NoFieldForm;
import com.payline.pmapi.bean.paymentform.request.PaymentFormConfigurationRequest;
import com.payline.pmapi.bean.paymentform.response.configuration.PaymentFormConfigurationResponse;
import com.payline.pmapi.bean.paymentform.response.configuration.impl.PaymentFormConfigurationResponseSpecific;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.seleniumhq.jetty9.io.ssl.ALPNProcessor;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

class PaymentFormConfigurationServiceImplTest {
    @InjectMocks
    PaymentFormConfigurationServiceImpl underTest = new PaymentFormConfigurationServiceImpl();

    @Mock
    FormUtils formUtils;

    @Mock
    ClientUtils clientUtils;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Nested
    class getPaymentFormConfiguration {

        @Test
        void onInitForm() {
            final PaymentFormConfigurationResponse response = underTest.getPaymentFormConfiguration(MockUtils.aPaymentFormConfigurationRequest());
            assertEquals(PaymentFormConfigurationResponseSpecific.class, response.getClass());
            final PaymentFormConfigurationResponseSpecific responseSpecific = (PaymentFormConfigurationResponseSpecific) response;
            assertEquals(NoFieldForm.class, responseSpecific.getPaymentForm().getClass());
            final NoFieldForm noFieldForm = (NoFieldForm) responseSpecific.getPaymentForm();
            assertTrue(noFieldForm.isDisplayButton());
            assertEquals("Payer avec Amazon Pay", noFieldForm.getButtonText());
            assertEquals(StringUtils.EMPTY, noFieldForm.getDescription());
        }

        @Test
        void onStepComplete() {
            final Map<String, String> requestData = new HashMap<>();
            requestData.put(RequestContextKeys.CHECKOUT_SESSION_ID, "123456");
            requestData.put(RequestContextKeys.STEP, RequestContextKeys.STEP_COMPLETE);

            final PaymentFormConfigurationRequest paymentFormConfigurationRequest =
                    MockUtils.aPaymentFormConfigurationRequestBuilder()
                            .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext()
                            .withRequestData(requestData).build())
                            .build();

            final CheckoutSession checkoutSession = CheckoutSession.builder()
                    .buyer(Buyer.builder().name("Toto Titi").email("test@test.fr").build()).build();
            doReturn(checkoutSession).when(clientUtils).getCheckoutSession(eq("123456"));
            final PaymentFormConfigurationResponse response = underTest.getPaymentFormConfiguration(paymentFormConfigurationRequest);
            assertNotNull(response);
            assertTrue(response instanceof PaymentFormConfigurationResponseSpecific);
            final PaymentFormConfigurationResponseSpecific specificResponse = (PaymentFormConfigurationResponseSpecific) response;
            CustomForm form = (CustomForm) specificResponse.getPaymentForm();
            assertEquals("Confirmer", form.getButtonText());
            assertEquals("Récapitulatif de la commande", form.getDescription());
            assertTrue(form.isDisplayButton());
            final List<PaymentFormField> paymentFormFieldList = form.getCustomFields();
            assertEquals("Email: test@test.fr",((PaymentFormDisplayFieldText)paymentFormFieldList.get(0)).getContent());
            assertEquals("Nom: Toto Titi",((PaymentFormDisplayFieldText)paymentFormFieldList.get(1)).getContent());
            assertEquals("Montant: 10.00€",((PaymentFormDisplayFieldText)paymentFormFieldList.get(2)).getContent());
        }
    }
}