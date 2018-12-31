/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.tradefed.contentprovider;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link android.tradefed.contentprovider.ManagedFileContentProvider}. TODO: Complete the
 * tests when automatic test setup is made.
 */
@RunWith(AndroidJUnit4.class)
public class ManagedFileContentProviderTest {

    public static final String CONTENT_PROVIDER =
            String.format("%s://android.tradefed.contentprovider", ContentResolver.SCHEME_CONTENT);
    private static final String TEST_FILE = "ManagedFileContentProviderTest.txt";

    private File mTestFile = null;
    private Context mAppContext;
    private List<Uri> mShouldBeCleaned = new ArrayList<>();
    private ContentValues mCv;
    private Uri mTestUri;

    @Before
    public void setUp() throws Exception {
        mCv = new ContentValues();
        mTestFile = new File(Environment.getExternalStorageDirectory(), TEST_FILE);
        if (mTestFile.exists()) {
            mTestFile.delete();
        }
        mTestFile.createNewFile();
        // Context of the app under test.
        mAppContext = InstrumentationRegistry.getTargetContext();
        assertEquals("android.tradefed.contentprovider.test", mAppContext.getPackageName());

        String fullUriPath = String.format("%s%s", CONTENT_PROVIDER, mTestFile.getAbsolutePath());
        mTestUri = Uri.parse(fullUriPath);
    }

    @After
    public void tearDown() {
        if (mTestFile != null) {
            mTestFile.delete();
        }
        for (Uri uri : mShouldBeCleaned) {
            mAppContext
                    .getContentResolver()
                    .delete(
                            uri,
                            /** selection * */
                            null,
                            /** selectionArgs * */
                            null);
        }
    }

    /** Test that we can delete a file from the content provider. */
    @Test
    public void testDelete() throws Exception {
        ContentResolver resolver = mAppContext.getContentResolver();
        Uri uriResult = resolver.insert(mTestUri, mCv);
        mShouldBeCleaned.add(mTestUri);
        // Insert is successful
        assertEquals(mTestUri, uriResult);
        // Trying to insert again is inop
        Uri reInsert = resolver.insert(mTestUri, mCv);
        assertNull(reInsert);
        // Now delete
        int affected =
                resolver.delete(
                        mTestUri,
                        /** selection * */
                        null,
                        /** selectionArgs * */
                        null);
        assertEquals(1, affected);
        // File should have been deleted.
        assertFalse(mTestFile.exists());
        // We can now insert again
        mTestFile.createNewFile();
        uriResult = resolver.insert(mTestUri, mCv);
        assertEquals(mTestUri, uriResult);
    }

    /** Test that querying the content provider is working. */
    @Test
    public void testQuery() throws Exception {
        ContentResolver resolver = mAppContext.getContentResolver();
        Uri uriResult = resolver.insert(mTestUri, mCv);
        mShouldBeCleaned.add(mTestUri);
        // Insert is successful
        assertEquals(mTestUri, uriResult);

        Cursor cursor =
                resolver.query(
                        mTestUri,
                        /** projection * */
                        null,
                        /** selection * */
                        null,
                        /** selectionArgs* */
                        null,
                        /** sortOrder * */
                        null);
        try {
            assertEquals(1, cursor.getCount());
            String[] columns = cursor.getColumnNames();
            assertEquals(ManagedFileContentProvider.COLUMNS, columns);
            assertTrue(cursor.moveToNext());
            // Absolute path
            assertEquals(
                    Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + TEST_FILE,
                    cursor.getString(0));
            // Uri
            assertEquals(mTestUri.toString(), cursor.getString(1));
            // Type
            assertEquals("text/plain", cursor.getString(2));
            // Metadata
            assertNull(cursor.getString(3));
        } finally {
            cursor.close();
        }
    }

    /** Test that querying the content provider is working when abstracting the sdcard */
    @Test
    public void testQuery_sdcard() throws Exception {
        ContentResolver resolver = mAppContext.getContentResolver();
        Uri uriResult = resolver.insert(mTestUri, mCv);
        mShouldBeCleaned.add(mTestUri);
        // Insert is successful
        assertEquals(mTestUri, uriResult);

        String sdcardUriPath = String.format("%s/sdcard/%s", CONTENT_PROVIDER, mTestFile.getName());
        Uri sdcardUri = Uri.parse(sdcardUriPath);

        Cursor cursor =
                resolver.query(
                        sdcardUri,
                        /** projection * */
                        null,
                        /** selection * */
                        null,
                        /** selectionArgs* */
                        null,
                        /** sortOrder * */
                        null);
        try {
            assertEquals(1, cursor.getCount());
            String[] columns = cursor.getColumnNames();
            assertEquals(ManagedFileContentProvider.COLUMNS, columns);
            assertTrue(cursor.moveToNext());
            // Absolute path
            assertEquals(
                    Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + TEST_FILE,
                    cursor.getString(0));
            // Uri
            assertEquals(sdcardUri.toString(), cursor.getString(1));
            // Type
            assertEquals("text/plain", cursor.getString(2));
            // Metadata
            assertNull(cursor.getString(3));
        } finally {
            cursor.close();
        }
    }
}
