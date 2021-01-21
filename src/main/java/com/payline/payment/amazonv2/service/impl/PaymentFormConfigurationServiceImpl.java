package com.payline.payment.amazonv2.service.impl;

import com.payline.payment.amazonv2.bean.CheckoutSession;
import com.payline.payment.amazonv2.service.AbstractLogoPaymentFormConfigurationService;
import com.payline.payment.amazonv2.utils.amazon.ClientUtils;
import com.payline.payment.amazonv2.utils.constant.RequestContextKeys;
import com.payline.payment.amazonv2.utils.form.FormUtils;
import com.payline.pmapi.bean.paymentform.bean.form.NoFieldForm;
import com.payline.pmapi.bean.paymentform.request.PaymentFormConfigurationRequest;
import com.payline.pmapi.bean.paymentform.response.configuration.PaymentFormConfigurationResponse;
import com.payline.pmapi.bean.paymentform.response.configuration.impl.PaymentFormConfigurationResponseSpecific;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PaymentFormConfigurationServiceImpl extends AbstractLogoPaymentFormConfigurationService {
    private final FormUtils formUtils = FormUtils.getInstance();

    public static final String NOFIELDFORM_BUTTON_TEXT = "form.button.amazonPay.text";
    public static final String NOFIELDFORM_BUTTON_DESCRIPTION = "form.button.amazonPay.description";

    private ClientUtils client = ClientUtils.getInstance();

    @Override
    public PaymentFormConfigurationResponse getPaymentFormConfiguration(final PaymentFormConfigurationRequest request) {

        final PaymentFormConfigurationResponse paymentFormConfigurationResponse;
        final String step = request.getRequestContext() != null && request.getRequestContext().getRequestData() != null ?
                request.getRequestContext().getRequestData().get(RequestContextKeys.STEP) : null;

        if (RequestContextKeys.STEP_COMPLETE.equalsIgnoreCase(step)) {
            final String checkoutSessionId = request.getRequestContext().getRequestData().get(RequestContextKeys.CHECKOUT_SESSION_ID);
            final CheckoutSession session = client.getCheckoutSession(checkoutSessionId);
            paymentFormConfigurationResponse = formUtils.createPaymentInfoDisplayForm(session, request);
        } else {
            final NoFieldForm noFieldForm = NoFieldForm.NoFieldFormBuilder
                    .aNoFieldForm()
                    .withDisplayButton(true)
                    .withButtonText(i18n.getMessage(NOFIELDFORM_BUTTON_TEXT, request.getLocale()))
                    .withDescription(i18n.getMessage(NOFIELDFORM_BUTTON_DESCRIPTION, request.getLocale()))
                    .build();
            paymentFormConfigurationResponse = PaymentFormConfigurationResponseSpecific.PaymentFormConfigurationResponseSpecificBuilder
                    .aPaymentFormConfigurationResponseSpecific()
                    .withPaymentForm(noFieldForm)
                    .build();
        }

        return paymentFormConfigurationResponse;

    }

}
