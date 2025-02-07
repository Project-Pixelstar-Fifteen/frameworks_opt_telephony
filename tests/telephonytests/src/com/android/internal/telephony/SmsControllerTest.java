/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.internal.telephony.uicc.AdnRecord;
import com.android.internal.telephony.uicc.AdnRecordCache;
import com.android.internal.telephony.uicc.IccConstants;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.NoSuchElementException;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class SmsControllerTest extends TelephonyTest {
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    // Mocked classes
    private AdnRecordCache mAdnRecordCache;

    // SmsController under test
    private SmsController mSmsControllerUT;
    private final String smscAddrStr = "+1206313004";
    private String mCallingPackage;
    private int mCallingUserId;

    @Before
    public void setUp() throws Exception {
        super.setUp(getClass().getSimpleName());
        mAdnRecordCache = Mockito.mock(AdnRecordCache.class);
        mSmsControllerUT = new SmsController(mContext, mFeatureFlags);
        mCallingPackage = mContext.getOpPackageName();
        mCallingUserId = Binder.getCallingUserHandle().getIdentifier();

        doReturn(true).when(mPackageManager).hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING);
    }

    @After
    public void tearDown() throws Exception {
        mAdnRecordCache = null;
        WapPushCache.clear();
        super.tearDown();
    }

    private void fdnCheckSetup() {
        // FDN check setup
        doReturn(mAdnRecordCache).when(mSimRecords).getAdnCache();
        doReturn(mUiccProfile).when(mUiccController).getUiccProfileForPhone(anyInt());
        doReturn(true).when(mUiccCardApplication3gpp).getIccFdnAvailable();
        doReturn(true).when(mUiccCardApplication3gpp).getIccFdnEnabled();
        doReturn(false).when(mTelephonyManager).isEmergencyNumber(anyString());
        doReturn("us").when(mTelephonyManager).getSimCountryIso();
        doReturn(smscAddrStr).when(mIccSmsInterfaceManager).getSmscAddressFromIccEf(anyString());
    }

    private void fdnCheckCleanup() {
        doReturn(false).when(mUiccCardApplication3gpp).getIccFdnAvailable();
        doReturn(false).when(mUiccCardApplication3gpp).getIccFdnEnabled();
    }

    @Test
    public void isNumberBlockedByFdn_fdnListHasBothDestAddrAndSmscAddr() {
        // FDN check setup
        fdnCheckSetup();
        ArrayList<AdnRecord> fdnList = new ArrayList<>();
        doReturn(fdnList).when(mAdnRecordCache).getRecordsIfLoaded(IccConstants.EF_FDN);

        // FDN list has both destination addr and smsc addr
        AdnRecord smscAddrRecord = new AdnRecord(null, smscAddrStr);
        AdnRecord destAddrRecord = new AdnRecord(null, "1234");
        fdnList.add(0, smscAddrRecord);
        fdnList.add(1, destAddrRecord);

        // Returns false as list contains both dest addr and smsc addr
        assertFalse(mSmsControllerUT.isNumberBlockedByFDN(1, "1234",
                mCallingPackage));

        // Clean up
        fdnCheckCleanup();
    }

    @Test
    public void isNumberBlockedByFdn_fdnListHasDestAddr() {
        // FDN check setup
        fdnCheckSetup();
        ArrayList<AdnRecord> fdnList = new ArrayList<>();
        doReturn(fdnList).when(mAdnRecordCache).getRecordsIfLoaded(IccConstants.EF_FDN);

        // FDN list has only destination addr
        AdnRecord destAddrRecord = new AdnRecord(null, "1234");
        fdnList.add(0, destAddrRecord);

        // Returns true as list does not contain smsc addr
        assertTrue(mSmsControllerUT.isNumberBlockedByFDN(1, "1234", mCallingPackage));

        // Clean up
        fdnCheckCleanup();
    }

    @Test
    public void isNumberBlockedByFdn_fdnListHasSmscAddr() {
        // FDN check setup
        fdnCheckSetup();
        ArrayList<AdnRecord> fdnList = new ArrayList<>();
        doReturn(fdnList).when(mAdnRecordCache).getRecordsIfLoaded(IccConstants.EF_FDN);

        // FDN list has both destination addr and smsc addr
        AdnRecord smscAddrRecord = new AdnRecord(null, smscAddrStr);
        fdnList.add(0, smscAddrRecord);

        // Returns true as list does not contain dest addr
        assertTrue(mSmsControllerUT.isNumberBlockedByFDN(1, "1234", mCallingPackage));

        // Clean up
        fdnCheckCleanup();
    }

    @Test
    public void isNumberBlockedByFdn_destAddrIsEmergencyNumber() {
        // FDN check setup
        fdnCheckSetup();
        ArrayList<AdnRecord> fdnList = new ArrayList<>();
        doReturn(fdnList).when(mAdnRecordCache).getRecordsIfLoaded(IccConstants.EF_FDN);

        doReturn(true).when(mTelephonyManager).isEmergencyNumber(anyString());
        // Returns false as dest addr is emergency number
        assertFalse(mSmsControllerUT.isNumberBlockedByFDN(1, "1234",
                mCallingPackage));

        // Clean up
        fdnCheckCleanup();
    }

    @Test
    public void isNumberBlockedByFdn_fdnDisabled() {
        // FDN check setup
        fdnCheckSetup();

        doReturn(false).when(mUiccCardApplication3gpp).getIccFdnEnabled();
        // Returns false as fdn is not enabled
        assertFalse(mSmsControllerUT.isNumberBlockedByFDN(1, "1234",
                mCallingPackage));

        // Clean up
        fdnCheckCleanup();
    }

    @Test
    public void sendVisualVoicemailSmsForSubscriber_phoneIsNotInEcm() {
        assertFalse(mPhone.isInEcm());
        int subId = 1;
        doReturn(true).when(mSubscriptionManager)
                .isSubscriptionAssociatedWithUser(eq(subId), any());

        mSmsControllerUT.sendVisualVoicemailSmsForSubscriber(mCallingPackage, mCallingUserId,
                null , subId, null, 0, null, null);
        verify(mIccSmsInterfaceManager).sendTextWithSelfPermissions(any(), eq(mCallingUserId),
                any(), any(), any(), any(), any(), any(), eq(false), eq(true));
    }

    @Test
    public void sendVisualVoicemailSmsForSubscriber_phoneIsInEcm() {
        doReturn(true).when(mPhone).isInEcm();

        mSmsControllerUT.sendVisualVoicemailSmsForSubscriber(mCallingPackage, mCallingUserId,
                null , 1, null, 0, null, null);
        verify(mIccSmsInterfaceManager, never()).sendTextWithSelfPermissions(any(),
                eq(mCallingUserId), any(), any(), any(), any(), any(), any(),
                eq(false), eq(true));

        doReturn(false).when(mPhone).isInEcm();
    }

    @Test
    public void sendsendTextForSubscriberTest() {
        int subId = 1;
        doReturn(true).when(mSubscriptionManager)
                .isSubscriptionAssociatedWithUser(eq(subId), any());

        mSmsControllerUT.sendTextForSubscriber(subId, mCallingPackage, null, "1234",
                null, "text", null, null, false, 0L, true, true);
        verify(mIccSmsInterfaceManager, Mockito.times(1))
                .sendText(mCallingPackage, mCallingUserId,
                        "1234", null, "text", null, null, false, 0L, true);
    }

    @Test
    public void sendTextForSubscriberTest_InteractAcrossUsers() {
        int subId = 1;
        // Sending text to subscriber should not fail when the caller has the
        // INTERACT_ACROSS_USERS_FULL permission.
        doReturn(false).when(mSubscriptionManager)
                .isSubscriptionAssociatedWithUser(eq(subId), any());
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext).checkCallingOrSelfPermission(
                eq(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL));

        mSmsControllerUT.sendTextForSubscriber(subId, mCallingPackage, null, "1234",
                null, "text", null, null, false, 0L, true, true);
        verify(mIccSmsInterfaceManager, Mockito.times(1))
                .sendText(mCallingPackage, mCallingUserId,
                        "1234", null, "text", null, null, false, 0L, true);
    }

    @Test
    public void sendTextForSubscriberTestFail() {
        int subId = 1;
        // Sending text to subscriber should fail when the caller does not have the
        // INTERACT_ACROSS_USERS_FULL permission and is not associated with the subscription.
        doReturn(false).when(mSubscriptionManager)
                .isSubscriptionAssociatedWithUser(eq(subId), any());
        doReturn(PackageManager.PERMISSION_DENIED).when(mContext).checkCallingOrSelfPermission(
                eq(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL));

        mSmsControllerUT.sendTextForSubscriber(subId, mCallingPackage, null, "1234",
                null, "text", null, null, false, 0L, true, true);
        verify(mIccSmsInterfaceManager, Mockito.times(0))
                .sendText(mCallingPackage, mCallingUserId,
                        "1234", null, "text", null, null, false, 0L, true);
    }

    @Test
    public void testGetWapMessageSize() {
        long expectedSize = 100L;
        String location = "content://mms";
        byte[] locationBytes = location.getBytes(StandardCharsets.ISO_8859_1);
        byte[] transactionId = "123".getBytes(StandardCharsets.ISO_8859_1);

        WapPushCache.putWapMessageSize(locationBytes, transactionId, expectedSize);
        long size = mSmsControllerUT.getWapMessageSize(location);

        assertEquals(expectedSize, size);
    }

    @Test
    public void testGetWapMessageSize_withTransactionIdAppended() {
        long expectedSize = 100L;
        byte[] location = "content://mms".getBytes(StandardCharsets.ISO_8859_1);
        byte[] transactionId = "123".getBytes(StandardCharsets.ISO_8859_1);
        byte[] joinedKey = new byte[location.length + transactionId.length];
        System.arraycopy(location, 0, joinedKey, 0, location.length);
        System.arraycopy(transactionId, 0, joinedKey, location.length, transactionId.length);
        String joinedKeyString = new String(joinedKey, StandardCharsets.ISO_8859_1);

        WapPushCache.putWapMessageSize(location, transactionId, expectedSize);
        long size = mSmsControllerUT.getWapMessageSize(joinedKeyString);

        assertEquals(expectedSize, size);
    }

    @Test
    public void testGetWapMessageSize_nonexistentThrows() {
        assertThrows(NoSuchElementException.class, () ->
                mSmsControllerUT.getWapMessageSize("content://mms")
        );
    }

    @Test
    @EnableCompatChanges({TelephonyManager.ENABLE_FEATURE_MAPPING})
    public void sendTextForSubscriberTestEnabledTelephonyFeature() throws Exception {
        int subId = 1;
        doReturn(true).when(mSubscriptionManager)
                .isSubscriptionAssociatedWithUser(eq(subId), any());

        // Replace field to set SDK version of vendor partition to Android V
        int vendorApiLevel = Build.VERSION_CODES.VANILLA_ICE_CREAM;
        replaceInstance(SmsController.class, "mVendorApiLevel", mSmsControllerUT, vendorApiLevel);

        // Feature enabled, device does not have required telephony feature.
        doReturn(true).when(mFeatureFlags).enforceTelephonyFeatureMappingForPublicApis();
        doReturn(false).when(mPackageManager).hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING);

        assertThrows(UnsupportedOperationException.class,
                () -> mSmsControllerUT.sendTextForSubscriber(subId, mCallingPackage, null, "1234",
                        null, "text", null, null, false, 0L, true, true));

        // Device has required telephony feature.
        doReturn(true).when(mPackageManager).hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_MESSAGING);
        mSmsControllerUT.sendTextForSubscriber(subId, mCallingPackage, null, "1234",
                null, "text", null, null, false, 0L, true, true);
        verify(mIccSmsInterfaceManager, Mockito.times(1))
                .sendText(mCallingPackage, mCallingUserId,
                        "1234", null, "text", null, null, false, 0L, true);
    }
}