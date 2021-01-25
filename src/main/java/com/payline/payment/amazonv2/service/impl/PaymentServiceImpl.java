package com.payline.payment.amazonv2.service.impl;

import com.payline.payment.amazonv2.bean.CheckoutSession;
import com.payline.payment.amazonv2.bean.Script;
import com.payline.payment.amazonv2.bean.configuration.RequestConfiguration;
import com.payline.payment.amazonv2.bean.nested.MerchantMetadata;
import com.payline.payment.amazonv2.bean.nested.PaymentDetails;
import com.payline.payment.amazonv2.bean.nested.Price;
import com.payline.payment.amazonv2.bean.nested.WebCheckoutDetails;
import com.payline.payment.amazonv2.exception.PluginException;
import com.payline.payment.amazonv2.service.RequestConfigurationService;
import com.payline.payment.amazonv2.utils.JsonService;
import com.payline.payment.amazonv2.utils.PluginUtils;
import com.payline.payment.amazonv2.utils.amazon.ClientUtils;
import com.payline.payment.amazonv2.utils.constant.ContractConfigurationKeys;
import com.payline.payment.amazonv2.utils.constant.PartnerConfigurationKeys;
import com.payline.payment.amazonv2.utils.constant.RequestContextKeys;
import com.payline.payment.amazonv2.utils.form.FormUtils;
import com.payline.payment.amazonv2.utils.i18n.I18nService;
import com.payline.pmapi.bean.common.FailureCause;
import com.payline.pmapi.bean.payment.RequestContext;
import com.payline.pmapi.bean.payment.request.PaymentRequest;
import com.payline.pmapi.bean.payment.response.PaymentResponse;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseFailure;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseFormUpdated;
import com.payline.pmapi.bean.payment.response.impl.PaymentResponseRedirect;
import com.payline.pmapi.bean.paymentform.bean.form.PartnerWidgetForm;
import com.payline.pmapi.bean.paymentform.bean.form.partnerwidget.PartnerWidgetContainer;
import com.payline.pmapi.bean.paymentform.bean.form.partnerwidget.PartnerWidgetContainerTargetDivId;
import com.payline.pmapi.bean.paymentform.bean.form.partnerwidget.PartnerWidgetOnPay;
import com.payline.pmapi.bean.paymentform.bean.form.partnerwidget.PartnerWidgetOnPayCallBack;
import com.payline.pmapi.bean.paymentform.bean.form.partnerwidget.PartnerWidgetScriptImport;
import com.payline.pmapi.bean.paymentform.response.configuration.PaymentFormConfigurationResponse;
import com.payline.pmapi.bean.paymentform.response.configuration.impl.PaymentFormConfigurationResponseSpecific;
import com.payline.pmapi.service.PaymentService;
import lombok.extern.log4j.Log4j2;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
@Log4j2
public class PaymentServiceImpl implements PaymentService {

    private ClientUtils client = ClientUtils.getInstance();
    private FormUtils formUtils = FormUtils.getInstance();
    protected I18nService i18n = I18nService.getInstance();
    private final JsonService jsonService = JsonService.getInstance();

    public static final String FAILURE_TRANSACTION_ID = "NO TRANSACTION YET";
    protected static final String SCRIPT_BEFORE_IMPORT = "var $ = Payline.jQuery;";


    @Override
    public PaymentResponse paymentRequest(PaymentRequest request) {

        PaymentResponse paymentResponse;
        final String step = request.getRequestContext() != null && request.getRequestContext().getRequestData() != null ?
                request.getRequestContext().getRequestData().get(RequestContextKeys.STEP) : null;

        //Dans le cas ou l'on est déja passé par le choix du mode de paiement Amazon.
        //On redirige l'utilisateur vers l'URL de paiement fourni par Amazon.
        if (RequestContextKeys.STEP_COMPLETE.equalsIgnoreCase(step)) {
            final String checkoutSessionId = request.getRequestContext().getRequestData().get(RequestContextKeys.CHECKOUT_SESSION_ID);
            final RequestConfiguration configuration = RequestConfigurationService.getInstance().build(request);

            final CheckoutSession checkoutSession = createCheckoutSessionFromPaymentRequest(configuration, request);
            client.init(configuration);
            CheckoutSession checkoutUpdated = client.updateCheckoutSession(checkoutSessionId, checkoutSession);
            return createPaymentResponseFromCheckoutSession(checkoutUpdated);
        }
        //Sinon on redirige l'utilisateur vers le premier formulaire ou l'on affiche
        //le bouton Amazon.
        else {
            paymentResponse = getFirstForm(request);
        }
        return paymentResponse;
    }

    protected PaymentResponse getFirstForm(final PaymentRequest request) {
        PaymentResponse paymentResponse = null;
        try {
            final String description = i18n.getMessage("widget.description", request.getLocale());
            final URL url = new URL(request.getPartnerConfiguration().getProperty(PartnerConfigurationKeys.AMAZON_SCRIPT_URL));
            final PartnerWidgetScriptImport scriptImport = PartnerWidgetScriptImport.WidgetPartnerScriptImportBuilder
                    .aWidgetPartnerScriptImport()
                    .withAsync(false)
                    .withCache(false)
                    .withUrl(url)
                    .build();

            final Script script = formUtils.createScript(request);

            // this object is not used because the SamsungPay widget is shown with an overlay and not in a specific div
            final PartnerWidgetContainer container = PartnerWidgetContainerTargetDivId.WidgetPartnerContainerTargetDivIdBuilder
                    .aWidgetPartnerContainerTargetDivId()
                    .withId("AmazonPayButton")
                    .build();

            // create an unused but mandatory PartnerWidgetOnPay
            final PartnerWidgetOnPay onPay = PartnerWidgetOnPayCallBack.WidgetContainerOnPayCallBackBuilder
                    .aWidgetContainerOnPayCallBack()
                    .withName("AmazonPayButton")
                    .build();

            final String scriptAfterImport = "amazon.Pay.renderButton('#AmazonPayButton', " + jsonService.toJson(script) + "); $('#AmazonPayButton').click();";
            final PartnerWidgetForm form = PartnerWidgetForm.WidgetPartnerFormBuilder
                    .aWidgetPartnerForm()
                    .withDescription(description)
                    .withDisplayButton(false)
                    .withScriptImport(scriptImport)
                    .withLoadingScriptBeforeImport(SCRIPT_BEFORE_IMPORT)
                    .withLoadingScriptAfterImport(scriptAfterImport)
                    .withContainer(container)
                    .withOnPay(onPay)// useless as the script directly redirect the buyer but mandatory by the API (shall not be null)
                    .withPerformsAutomaticRedirection(true)
                    .build();

            final PaymentFormConfigurationResponse paymentFormConfigurationResponse =
                    PaymentFormConfigurationResponseSpecific.PaymentFormConfigurationResponseSpecificBuilder
                            .aPaymentFormConfigurationResponseSpecific()
                            .withPaymentForm(form)
                            .build();

            paymentResponse = PaymentResponseFormUpdated.PaymentResponseFormUpdatedBuilder
                    .aPaymentResponseFormUpdated()
                    .withPaymentFormConfigurationResponse(paymentFormConfigurationResponse)
                    .build();
        } catch (MalformedURLException e) {
            String errorMessage = "Unable convert Amazon script url into an URL object";
            log.error(errorMessage, e);
            paymentResponse = PaymentResponseFailure.PaymentResponseFailureBuilder
                    .aPaymentResponseFailure()
                    .withPartnerTransactionId(FAILURE_TRANSACTION_ID)
                    .withErrorCode(errorMessage)
                    .withFailureCause(FailureCause.INVALID_DATA)
                    .build();
        } catch (PluginException e) {
            log.info("unable to execute PaymentService#paymentRequest", e);
            paymentResponse = PaymentResponseFailure.PaymentResponseFailureBuilder
                    .aPaymentResponseFailure()
                    .withPartnerTransactionId(FAILURE_TRANSACTION_ID)
                    .withErrorCode(e.getMessage())
                    .withFailureCause(e.getFailureCause())
                    .build();
        } catch (RuntimeException e) {
            paymentResponse = PaymentResponseFailure.PaymentResponseFailureBuilder
                    .aPaymentResponseFailure()
                    .withPartnerTransactionId(FAILURE_TRANSACTION_ID)
                    .withErrorCode(e.getMessage())
                    .withFailureCause(FailureCause.INTERNAL_ERROR)
                    .build();
        }
        return paymentResponse;
    }

    private CheckoutSession createCheckoutSessionFromPaymentRequest(RequestConfiguration configuration, PaymentRequest request) {
        WebCheckoutDetails webCheckoutDetails = WebCheckoutDetails.builder()
                .checkoutResultReturnUrl(configuration.getEnvironment().getRedirectionReturnURL())
                .build();

        Price chargeAmount = Price.builder()
                .amount(PluginUtils.createStringAmount(request.getAmount()))
                .currencyCode(request.getAmount().getCurrency().getCurrencyCode())
                .build();

        PaymentDetails paymentDetails = PaymentDetails.builder()
                .paymentIntent(request.isCaptureNow() ? PaymentDetails.PaymentIntent.AuthorizeWithCapture : PaymentDetails.PaymentIntent.Authorize)
                .canHandlePendingAuthorization(false)
                .softDescriptor(request.getSoftDescriptor())
                .chargeAmount(chargeAmount)
                .build();

        MerchantMetadata merchantMetadata = MerchantMetadata.builder()
                .merchantReferenceId(request.getTransactionId())
                .merchantStoreName(configuration.getContractConfiguration().getProperty(ContractConfigurationKeys.MERCHANT_NAME).getValue())
                .noteToBuyer(request.getOrder().getReference())
                .build();

        return CheckoutSession.builder()
                .merchantMetadata(merchantMetadata)
                .webCheckoutDetails(webCheckoutDetails)
                .paymentDetails(paymentDetails)
                .build();
    }

    private PaymentResponse createPaymentResponseFromCheckoutSession(CheckoutSession checkoutSession) {
        PaymentResponse paymentResponse;
        String checkoutSessionId = checkoutSession.getCheckoutSessionId();

        try {
            Map<String, String> requestData = new HashMap<>();
            requestData.putIfAbsent(RequestContextKeys.CHECKOUT_SESSION_ID, checkoutSessionId);
            requestData.put(RequestContextKeys.EMAIL, checkoutSession.getBuyer().getEmail());
            requestData.put(RequestContextKeys.STEP, RequestContextKeys.STEP_COMPLETE);

            RequestContext requestContext = RequestContext.RequestContextBuilder
                    .aRequestContext()
                    .withRequestData(requestData)
                    .build();

            PaymentResponseRedirect.RedirectionRequest redirectionRequest = PaymentResponseRedirect.RedirectionRequest.RedirectionRequestBuilder
                    .aRedirectionRequest()
                    .withRequestType(PaymentResponseRedirect.RedirectionRequest.RequestType.GET)
                    .withUrl(new URL(checkoutSession.getWebCheckoutDetails().getAmazonPayRedirectUrl()))
                    .build();

            paymentResponse = PaymentResponseRedirect.PaymentResponseRedirectBuilder
                    .aPaymentResponseRedirect()
                    .withPartnerTransactionId(checkoutSessionId)
                    .withRequestContext(requestContext)
                    .withRedirectionRequest(redirectionRequest)
                    .withStatusCode(checkoutSession.getStatusDetails().getState())
                    .build();
        } catch (MalformedURLException e) {
            String errorMessage = "Unable to convert AmazonPayRedirectUrl into an URL";
            log.error(errorMessage);
            paymentResponse = PaymentResponseFailure.PaymentResponseFailureBuilder
                    .aPaymentResponseFailure()
                    .withPartnerTransactionId(checkoutSessionId)
                    .withErrorCode(errorMessage)
                    .withFailureCause(FailureCause.INVALID_DATA)
                    .build();
        }
        return paymentResponse;
    }
}
