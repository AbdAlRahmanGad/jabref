package net.sf.jabref.logic.autocompleter;

import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.sf.jabref.model.entry.BibtexEntry;

public class EntireFieldAutoCompleterTest {

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void initAutoCompleterWithNullPreferenceThrowsException() {
        new EntireFieldAutoCompleter("field", null);
    }

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void initAutoCompleterWithNullFieldThrowsException() {
        new EntireFieldAutoCompleter(null, mock(AutoCompletePreferences.class));
    }

    @Test
    public void completeWithoutAddingAnythingReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        List<String> result = autoCompleter.complete("test");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeAfterAddingNullReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        autoCompleter.addBibtexEntry(null);

        List<String> result = autoCompleter.complete("test");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeAfterAddingEmptyEntryReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("test");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeAfterAddingEntryWithoutFieldReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("title", "testTitle");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("test");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeValueReturnsValue() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "testValue");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("testValue");
        Assert.assertEquals(Arrays.asList("testValue"), result);
    }

    @Test
    public void completeBeginnigOfValueReturnsValue() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "testValue");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("test");
        Assert.assertEquals(Arrays.asList("testValue"), result);
    }

    @Test
    public void completeLowercaseValueReturnsValue() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "testValue");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("testvalue");
        Assert.assertEquals(Arrays.asList("testValue"), result);
    }

    @Test
    public void completeNullReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "testKey");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete(null);
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeEmptyStringReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "testKey");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeReturnsMultipleResults() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entryOne = new BibtexEntry();
        entryOne.setField("field", "testValueOne");
        autoCompleter.addBibtexEntry(entryOne);
        BibtexEntry entryTwo = new BibtexEntry();
        entryTwo.setField("field", "testValueTwo");
        autoCompleter.addBibtexEntry(entryTwo);

        List<String> result = autoCompleter.complete("testValue");
        Assert.assertEquals(Arrays.asList("testValueOne", "testValueTwo"), result);
    }

    @Test
    public void completeShortStringReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "val");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("va");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeTooShortInputReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        when(preferences.getShortestLengthToComplete()).thenReturn(100);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "testValue");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("test");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeBeginnigOfSecondWordReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "test value");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("val");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completePartOfWordReturnsNothing() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "test value");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("lue");
        Assert.assertEquals(Collections.emptyList(), result);
    }

    @Test
    public void completeReturnsWholeFieldValue() {
        AutoCompletePreferences preferences = mock(AutoCompletePreferences.class);
        EntireFieldAutoCompleter autoCompleter = new EntireFieldAutoCompleter("field", preferences);

        BibtexEntry entry = new BibtexEntry();
        entry.setField("field", "test value");
        autoCompleter.addBibtexEntry(entry);

        List<String> result = autoCompleter.complete("te");
        Assert.assertEquals(Arrays.asList("test value"), result);
    }
}
