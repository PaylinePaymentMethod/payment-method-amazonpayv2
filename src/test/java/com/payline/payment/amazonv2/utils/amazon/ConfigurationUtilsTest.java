package com.payline.payment.amazonv2.utils.amazon;

import com.amazon.pay.api.types.Environment;
import com.amazon.pay.api.types.Region;
import com.payline.payment.amazonv2.MockUtils;
import com.payline.payment.amazonv2.bean.configuration.RequestConfiguration;
import com.payline.payment.amazonv2.exception.PluginException;
import com.payline.payment.amazonv2.utils.constant.PartnerConfigurationKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationUtilsTest {

    @Test
    void initTestOK() {
        // Data
        final ConfigurationUtils underTest = new ConfigurationUtils();
        final RequestConfiguration requestConfiguration = new RequestConfiguration(MockUtils.aContractConfiguration(), MockUtils.anEnvironment(), MockUtils.aPartnerConfiguration());

        assertNull(underTest.payConfiguration);

        // Test
        underTest.init(requestConfiguration);

        // Verif
        assertNotNull(underTest.payConfiguration);
        assertEquals(Region.EU, underTest.payConfiguration.getRegion());
        assertEquals("publicKeyId", underTest.payConfiguration.getPublicKeyId());
        assertEquals("RSA", underTest.payConfiguration.getPrivateKey().getAlgorithm());
        assertEquals("PKCS#8", underTest.payConfiguration.getPrivateKey().getFormat());
        assertEquals(Environment.SANDBOX, underTest.payConfiguration.getEnvironment());
    }

    @Test
    void initTestShouldThrowPluginException() {
        // Data
        final ConfigurationUtils underTest = new ConfigurationUtils();
        final RequestConfiguration requestConfiguration = new RequestConfiguration(MockUtils.aContractConfiguration(), MockUtils.anEnvironment(), MockUtils.aPartnerConfiguration());
        requestConfiguration.getPartnerConfiguration().getSensitiveProperties().replace(PartnerConfigurationKeys.PRIVATE_KEY, "TUTU !");

        assertNull(underTest.payConfiguration);

        // Test
        final PluginException pluginException = assertThrows(PluginException.class, () -> {
            underTest.init(requestConfiguration);
        });

        // Verif
        assertNotNull(pluginException);
        assertEquals("unable to init Amazon configuration", pluginException.getMessage());
    }
}