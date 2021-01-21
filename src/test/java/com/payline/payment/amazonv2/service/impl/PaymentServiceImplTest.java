package com.payline.payment.amazonv2.service.impl;

import com.payline.payment.amazonv2.MockUtils;
import com.payline.payment.amazonv2.bean.CheckoutSession;
import com.payline.payment.amazonv2.bean.nested.Buyer;
import com.payline.payment.amazonv2.bean.nested.StatusDetails;
import com.payline.payment.amazonv2.bean.nested.WebCheckoutDetails;
import com.payline.payment.amazonv2.utils.amazon.ClientUtils;
import com.payline.payment.amazonv2.utils.constant.PartnerConfigurationKeys;
import com.payline.payment.amazonv2.utils.constant.RequestContextKeys;
import com.payline.payment.amazonv2.utils.form.FormUtils;
import com.payline.pmapi.bean.common.FailureCause;
import com.payline.pmapi.bean.configuration.PartnerConfiguration;
import com.payline.pmapi.bean.payment.RequestContext;
import com.payline.pmapi.bean.payment.request.PaymentRequest;
import com.payline.pmapi.bean.payment.response.PaymentResponse;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseFailure;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseFormUpdated;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseRedirect;
import com.payline.pmapi.bean.paymentform.bean.form.PartnerWidgetForm;
import com.payline.pmapi.bean.paymentform.bean.form.partnerwidget.PartnerWidgetContainerTargetDivId;
import com.payline.pmapi.bean.paymentform.response.configuration.impl.PaymentFormConfigurationResponseSpecific;
import org.apache.commons.lang.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static com.payline.pmapi.bean.payment.response.impl.PaymentResponseRedirect.RedirectionRequest.RequestType.GET;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PaymentServiceImplTest {

    @Spy
    @InjectMocks
    private final PaymentServiceImpl underTest = new PaymentServiceImpl();

    @Mock
    private ClientUtils client;

    @Mock
    private FormUtils formUtils;

    private final String checkoutSessionId = "123456";
    private final String amazonPayUrl = "http://foo.bar/baz";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Nested
    class getFirstForm {

        @Test
        void nominalCase() throws MalformedURLException {
            final PaymentRequest request = MockUtils.aPaylinePaymentRequestBuilder()
                .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext().withRequestData(null).build())
                .build();
            final PaymentResponse paymentResponse = underTest.getFirstForm(request);
            assertNotNull(paymentResponse);
            assertTrue(paymentResponse instanceof PaymentResponseFormUpdated);
            final PaymentResponseFormUpdated paymentResponseFormUpdated = (PaymentResponseFormUpdated) paymentResponse;
            assertTrue(paymentResponseFormUpdated.getPaymentFormConfigurationResponse() instanceof PaymentFormConfigurationResponseSpecific);
            final PaymentFormConfigurationResponseSpecific paymentFormConfigurationResponseSpecific =
                    (PaymentFormConfigurationResponseSpecific) paymentResponseFormUpdated.getPaymentFormConfigurationResponse();
            final PartnerWidgetForm paymentForm = (PartnerWidgetForm) paymentFormConfigurationResponseSpecific.getPaymentForm();
            assertEquals("amazon.Pay.renderButton('#AmazonPayButton', null); $('#AmazonPayButton').click();", paymentForm.getLoadingScriptAfterImport());
            assertEquals(PaymentServiceImpl.SCRIPT_BEFORE_IMPORT, paymentForm.getLoadingScriptBeforeImport());
            assertEquals(new URL("https://static-eu.payments-amazon.com/checkout.js"), paymentForm.getScriptImport().getUrl());
            assertFalse(paymentForm.getScriptImport().isAsync());
            assertFalse(paymentForm.getScriptImport().isCache());
            assertTrue(paymentForm.isPerformsAutomaticRedirection());
            assertEquals(StringUtils.EMPTY, paymentForm.getDescription());
            assertNotNull(paymentForm.getPartnerWidgetOnPay());
            assertEquals("AmazonPayButton", ((PartnerWidgetContainerTargetDivId)paymentForm.getContainer()).getId());
        }

        @Test
        void whenMalformedURL() {
            Map<String,String> fakePartnerConfiguration = new HashMap<>();
            fakePartnerConfiguration.put(PartnerConfigurationKeys.AMAZON_SCRIPT_URL, "fake@email.com");
            final PartnerConfiguration partnerConfiguration = new PartnerConfiguration(fakePartnerConfiguration, new HashMap<>());
            final PaymentRequest request = MockUtils.aPaylinePaymentRequestBuilder()
                    .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext().withRequestData(null).build())
                    .withPartnerConfiguration(partnerConfiguration).build();
            final PaymentResponse paymentResponse = underTest.getFirstForm(request);
            assertNotNull(paymentResponse);
            assertTrue(paymentResponse instanceof PaymentResponseFailure);
            assertEquals(FailureCause.INVALID_DATA, ((PaymentResponseFailure) paymentResponse).getFailureCause());
        }

        @Test
        void whenRuntimeException() {
            final PaymentResponse paymentResponse = underTest.getFirstForm(null);
            assertNotNull(paymentResponse);
            assertTrue(paymentResponse instanceof PaymentResponseFailure);
            assertEquals(FailureCause.INTERNAL_ERROR, ((PaymentResponseFailure) paymentResponse).getFailureCause());
        }
    }

    @Nested
    class paymentRequest {

        @Test
        void onStepComplete() throws MalformedURLException {
            final Map<String, String> requestData = new HashMap<>();
            requestData.put(RequestContextKeys.CHECKOUT_SESSION_ID, checkoutSessionId);
            requestData.put(RequestContextKeys.STEP, RequestContextKeys.STEP_COMPLETE);
            final PaymentRequest request = MockUtils.aPaylinePaymentRequestBuilder()
                    .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext()
                            .withRequestData(requestData).build()).build();
            final CheckoutSession checkoutSession = CheckoutSession.builder()
                    .checkoutSessionId(checkoutSessionId)
                    .buyer(Buyer.builder().email("test@email.com").build())
                    .webCheckoutDetails(WebCheckoutDetails.builder()
                            .amazonPayRedirectUrl(amazonPayUrl).build())
                    .statusDetails(StatusDetails.builder().state("IN_PROGRESS").build())
                    .build();
            doReturn(checkoutSession).when(client).updateCheckoutSession(eq(checkoutSessionId), any());
            final PaymentResponse paymentResponse = underTest.paymentRequest(request);
            assertNotNull(paymentResponse);
            assertTrue(paymentResponse instanceof PaymentResponseRedirect);
            final PaymentResponseRedirect paymentResponseRedirect = (PaymentResponseRedirect) paymentResponse;
            assertEquals(checkoutSessionId, paymentResponseRedirect.getPartnerTransactionId());
            assertEquals("IN_PROGRESS", paymentResponseRedirect.getStatusCode());
            assertEquals(new URL(amazonPayUrl), paymentResponseRedirect.getRedirectionRequest().getUrl());
            assertEquals(GET, paymentResponseRedirect.getRedirectionRequest().getRequestType());
            final Map<String, String> requestDataResult = paymentResponseRedirect.getRequestContext().getRequestData();
            assertNotNull(requestDataResult);
            assertEquals(3, requestDataResult.size());
            assertEquals(checkoutSessionId, requestDataResult.get(RequestContextKeys.CHECKOUT_SESSION_ID));
            assertEquals("test@email.com", requestDataResult.get(RequestContextKeys.EMAIL));
            assertEquals(RequestContextKeys.STEP_COMPLETE, requestDataResult.get(RequestContextKeys.STEP));

        }

        @Test
        void onInitPayment() {
            final PaymentRequest request = MockUtils.aPaylinePaymentRequestBuilder()
                    .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext().withRequestData(null).build())
                    .build();
            underTest.paymentRequest(request);
            verify(underTest, times(1)).getFirstForm(eq(request));
        }
    }
}