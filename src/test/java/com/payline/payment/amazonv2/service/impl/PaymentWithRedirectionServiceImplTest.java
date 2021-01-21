package com.payline.payment.amazonv2.service.impl;

import com.payline.payment.amazonv2.MockUtils;
import com.payline.payment.amazonv2.bean.CheckoutSession;
import com.payline.payment.amazonv2.bean.Refund;
import com.payline.payment.amazonv2.bean.nested.Buyer;
import com.payline.payment.amazonv2.bean.nested.StatusDetails;
import com.payline.payment.amazonv2.exception.InvalidDataException;
import com.payline.payment.amazonv2.utils.amazon.ClientUtils;
import com.payline.payment.amazonv2.utils.constant.RequestContextKeys;
import com.payline.pmapi.bean.common.FailureCause;
import com.payline.pmapi.bean.payment.RequestContext;
import com.payline.pmapi.bean.payment.request.RedirectionPaymentRequest;
import com.payline.pmapi.bean.payment.request.TransactionStatusRequest;
import com.payline.pmapi.bean.payment.response.PaymentResponse;
import com.payline.pmapi.bean.payment.response.buyerpaymentidentifier.impl.Email;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseFailure;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseFormUpdated;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseSuccess;
import com.payline.pmapi.bean.paymentform.bean.form.NoFieldForm;
import com.payline.pmapi.bean.paymentform.response.configuration.impl.PaymentFormConfigurationResponseSpecific;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;


class PaymentWithRedirectionServiceImplTest {
    @InjectMocks
    PaymentWithRedirectionServiceImpl underTest = new PaymentWithRedirectionServiceImpl();

    @Mock
    ClientUtils client;

    private final String checkoutSessionId = "123321";
    private final String chargeId = "C01111111111";

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void finalizeRedirectionPaymentStep1Nominal() {
        CheckoutSession session = CheckoutSession.builder()
                .buyer(Buyer.builder().email("foo@bar.baz").name("Foo").build()).build();
        Mockito.doReturn(session).when(client).getCheckoutSession(any());

        Map<String, String[]> httpRequestParameters = new HashMap<>();
        httpRequestParameters.put("amazonCheckoutSessionId", new String[]{checkoutSessionId});

        RedirectionPaymentRequest request = RedirectionPaymentRequest.builder()
                .withHttpRequestParametersMap(httpRequestParameters)
                .withLocale(Locale.FRANCE)
                .withContractConfiguration(MockUtils.aContractConfiguration())
                .withPartnerConfiguration(MockUtils.aPartnerConfiguration())
                .withEnvironment(MockUtils.anEnvironment())
                .withTransactionId("UNKNOWN_TRANSACTION")
                .withAmount(MockUtils.aPaylineAmount())
                .withOrder(MockUtils.aPaylineOrder())
                .withBuyer(MockUtils.aBuyer())
                .withBrowser(MockUtils.aBrowser())
                .build();
        PaymentResponse response = underTest.finalizeRedirectionPayment(request);

        Assertions.assertNotNull(response);
        assertEquals(PaymentResponseFormUpdated.class, response.getClass());
        PaymentResponseFormUpdated responseFormUpdated = (PaymentResponseFormUpdated) response;

        assertEquals(PaymentFormConfigurationResponseSpecific.class, responseFormUpdated.getPaymentFormConfigurationResponse().getClass());
        final PaymentFormConfigurationResponseSpecific responseSpecific = (PaymentFormConfigurationResponseSpecific) responseFormUpdated.getPaymentFormConfigurationResponse();

        assertEquals(NoFieldForm.class, responseSpecific.getPaymentForm().getClass());
        /*CustomForm customForm = (CustomForm) responseSpecific.getPaymentForm();
        Assertions.assertEquals("Récapitulatif de la commande", customForm.getDescription());
        Assertions.assertEquals("Confirmer", customForm.getButtonText());

        Assertions.assertEquals(3, customForm.getCustomFields().size());
        Assertions.assertEquals("Email: foo@bar.baz", ((PaymentFormDisplayFieldText) customForm.getCustomFields().get(0)).getContent());
        Assertions.assertEquals("Nom: Foo", ((PaymentFormDisplayFieldText) customForm.getCustomFields().get(1)).getContent());
        Assertions.assertEquals("Montant: 10.00€", ((PaymentFormDisplayFieldText) customForm.getCustomFields().get(2)).getContent());
         */
    }

    @Test
    void finalizeRedirectionPaymentStep2Nominal() {
        CheckoutSession session = CheckoutSession.builder()
                .buyer(Buyer.builder().email("foo@bar.baz").name("Foo").build())
                .chargeId(chargeId)
                .statusDetails(StatusDetails.builder().state("Completed").build())
                .build();
        Mockito.doReturn(session).when(client).completeCheckoutSession(anyString(), any());

        Map<String, String> requestData = new HashMap<>();
        requestData.put(RequestContextKeys.STEP, RequestContextKeys.STEP_COMPLETE);
        requestData.put(RequestContextKeys.CHECKOUT_SESSION_ID, checkoutSessionId);
        requestData.put(RequestContextKeys.EMAIL, "foo@bar.baz");

        RequestContext context = RequestContext.RequestContextBuilder
                .aRequestContext()
                .withRequestData(requestData)
                .build();

        RedirectionPaymentRequest request = RedirectionPaymentRequest.builder()
                .withRequestContext(context)
                .withLocale(Locale.FRANCE)
                .withContractConfiguration(MockUtils.aContractConfiguration())
                .withPartnerConfiguration(MockUtils.aPartnerConfiguration())
                .withEnvironment(MockUtils.anEnvironment())
                .withTransactionId("UNKNOWN_TRANSACTION")
                .withAmount(MockUtils.aPaylineAmount())
                .withOrder(MockUtils.aPaylineOrder())
                .withBuyer(MockUtils.aBuyer())
                .withBrowser(MockUtils.aBrowser())
                .build();

        PaymentResponse response = underTest.finalizeRedirectionPayment(request);
        assertEquals(PaymentResponseSuccess.class, response.getClass());
        PaymentResponseSuccess responseSuccess = (PaymentResponseSuccess) response;

        assertEquals(chargeId, responseSuccess.getPartnerTransactionId());
        assertEquals("Completed", responseSuccess.getStatusCode());
        assertEquals(Email.class, responseSuccess.getTransactionDetails().getClass());
        Email email = (Email) responseSuccess.getTransactionDetails();

        assertEquals("foo@bar.baz", email.getEmail());
    }

    @Test
    void finalizeRedirectionPaymentRequestOtherStep(){
        final Map<String, String> requestData = new HashMap<>();
        requestData.put(RequestContextKeys.STEP, "foo");
        requestData.put(RequestContextKeys.CHECKOUT_SESSION_ID, checkoutSessionId);
        requestData.put(RequestContextKeys.EMAIL, "foo@bar.baz");

        final RequestContext context = RequestContext.RequestContextBuilder
                .aRequestContext()
                .withRequestData(requestData)
                .build();

        final RedirectionPaymentRequest request = RedirectionPaymentRequest.builder()
                .withRequestContext(context)
                .withLocale(Locale.FRANCE)
                .withContractConfiguration(MockUtils.aContractConfiguration())
                .withPartnerConfiguration(MockUtils.aPartnerConfiguration())
                .withEnvironment(MockUtils.anEnvironment())
                .withTransactionId("UNKNOWN_TRANSACTION")
                .withAmount(MockUtils.aPaylineAmount())
                .withOrder(MockUtils.aPaylineOrder())
                .withBuyer(MockUtils.aBuyer())
                .withBrowser(MockUtils.aBrowser())
                .build();

        final PaymentResponse response = underTest.finalizeRedirectionPayment(request);
        assertEquals(PaymentResponseFailure.class, response.getClass());
        PaymentResponseFailure responseFailure = (PaymentResponseFailure) response;
        assertEquals("Unknown step foo",responseFailure.getErrorCode());
        assertEquals(FailureCause.INVALID_DATA, responseFailure.getFailureCause());
    }

    @Test
    void finalizeRedirectionPaymentRequestPluginException(){
        Mockito.doThrow(new InvalidDataException("foo")).when(client).completeCheckoutSession(anyString(), any());

        final Map<String, String> requestData = new HashMap<>();
        requestData.put(RequestContextKeys.STEP, "foo");
        requestData.put(RequestContextKeys.CHECKOUT_SESSION_ID, checkoutSessionId);
        requestData.put(RequestContextKeys.EMAIL, "foo@bar.baz");

        final RequestContext context = RequestContext.RequestContextBuilder
                .aRequestContext()
                .withRequestData(requestData)
                .build();
        Map<String, String[]> httpRequestParameters = new HashMap<>();
            httpRequestParameters.put("amazonCheckoutSessionId", new String[]{checkoutSessionId});

        RedirectionPaymentRequest request = RedirectionPaymentRequest.builder()
                .withHttpRequestParametersMap(httpRequestParameters)
                .withLocale(Locale.FRANCE)
                .withContractConfiguration(MockUtils.aContractConfiguration())
                .withPartnerConfiguration(MockUtils.aPartnerConfiguration())
                .withEnvironment(MockUtils.anEnvironment())
                .withTransactionId("UNKNOWN_TRANSACTION")
                .withAmount(MockUtils.aPaylineAmount())
                .withOrder(MockUtils.aPaylineOrder())
                .withBuyer(MockUtils.aBuyer())
                .withBrowser(MockUtils.aBrowser())
                .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext()
                        .withRequestData(requestData).build())
                .build();
        PaymentResponse response = underTest.finalizeRedirectionPayment(request);
        assertEquals(PaymentResponseFailure.class, response.getClass());
        PaymentResponseFailure responseFailure = (PaymentResponseFailure) response;
        assertEquals("Unknown step foo",responseFailure.getErrorCode());
        assertEquals(FailureCause.INVALID_DATA, responseFailure.getFailureCause());
    }

    @Test
    void finalizeRedirectionPaymentRequestRuntimeException(){
        Mockito.doThrow(new NullPointerException("foo")).when(client).completeCheckoutSession(anyString(), any());

        Map<String, String> requestData = new HashMap<>();
        requestData.put(RequestContextKeys.CHECKOUT_SESSION_ID, "checkoutId");
        requestData.put(RequestContextKeys.STEP, RequestContextKeys.STEP_COMPLETE);
        Map<String, String[]> httpRequestParameters = new HashMap<>();
        httpRequestParameters.put("amazonCheckoutSessionId", new String[]{checkoutSessionId});

        RedirectionPaymentRequest request = RedirectionPaymentRequest.builder()
                .withHttpRequestParametersMap(httpRequestParameters)
                .withLocale(Locale.FRANCE)
                .withContractConfiguration(MockUtils.aContractConfiguration())
                .withPartnerConfiguration(MockUtils.aPartnerConfiguration())
                .withEnvironment(MockUtils.anEnvironment())
                .withTransactionId("UNKNOWN_TRANSACTION")
                .withAmount(MockUtils.aPaylineAmount())
                .withOrder(MockUtils.aPaylineOrder())
                .withBuyer(MockUtils.aBuyer())
                .withBrowser(MockUtils.aBrowser())
                .withRequestContext(RequestContext.RequestContextBuilder.aRequestContext()
                        .withRequestData(requestData).build())
                .build();
        PaymentResponse response = underTest.finalizeRedirectionPayment(request);
        assertEquals(PaymentResponseFailure.class, response.getClass());
        PaymentResponseFailure responseFailure = (PaymentResponseFailure) response;
        assertEquals("plugin error: NullPointerException: foo",responseFailure.getErrorCode());
        assertEquals(FailureCause.INTERNAL_ERROR, responseFailure.getFailureCause());
    }

    @Test
    void handleSessionExpiredPaymentNominal() {
        String transactionId = "123456789";
        TransactionStatusRequest request = MockUtils.aPaylineTransactionStatusRequestBuilder()
                .withTransactionId(transactionId)
                .build();

        CheckoutSession session = CheckoutSession.builder()
                .buyer(Buyer.builder().email("foo@bar.baz").name("Foo").build())
                .chargeId(chargeId)
                .statusDetails(StatusDetails.builder().state("Completed").build())
                .build();
        Mockito.doReturn(session).when(client).getCheckoutSession(any());
        PaymentResponse response = underTest.handleSessionExpired(request);

        assertEquals(PaymentResponseSuccess.class, response.getClass());
        PaymentResponseSuccess responseSuccess = (PaymentResponseSuccess) response;
        assertEquals("Completed", responseSuccess.getStatusCode());
        assertEquals(Email.class, responseSuccess.getTransactionDetails().getClass());
        Email email = (Email) responseSuccess.getTransactionDetails();

        assertEquals("foo@bar.baz", email.getEmail());
        Mockito.verify(client, Mockito.atLeastOnce()).getCheckoutSession(eq(transactionId));
        Mockito.verify(client, Mockito.never()).getRefund(any());
    }


    @Test
    void handleSessionExpiredRefundNominal() {
        String transactionId = "S02123456789";
        Refund refund = Refund.builder()
                .refundId(transactionId)
                .chargeId(transactionId)
                .statusDetails(StatusDetails.builder().state("Refunded").build())
                .build();
        Mockito.doReturn(refund).when(client).getRefund(any());

        TransactionStatusRequest request = MockUtils.aPaylineTransactionStatusRequestBuilder()
                .withTransactionId(transactionId)
                .build();

        PaymentResponse response = underTest.handleSessionExpired(request);
        assertEquals(PaymentResponseSuccess.class, response.getClass());
        PaymentResponseSuccess responseSuccess = (PaymentResponseSuccess) response;
        assertEquals(transactionId, responseSuccess.getPartnerTransactionId());
        assertEquals("Refunded", responseSuccess.getStatusCode());

        Mockito.verify(client, Mockito.atLeastOnce()).getRefund(eq(transactionId));
        Mockito.verify(client, Mockito.never()).getCheckoutSession(any());
    }
}