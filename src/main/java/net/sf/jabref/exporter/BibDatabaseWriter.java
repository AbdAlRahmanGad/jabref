/*  Copyright (C) 2003-2015 JabRef contributors.
 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along
 with this program; if not, write to the Free Software Foundation, Inc.,
 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package net.sf.jabref.exporter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.sf.jabref.logic.CustomEntryTypesManager;
import net.sf.jabref.model.entry.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jabref.*;
import net.sf.jabref.bibtex.BibEntryWriter;
import net.sf.jabref.bibtex.EntryTypes;
import net.sf.jabref.bibtex.comparator.BibtexStringComparator;
import net.sf.jabref.bibtex.comparator.CrossRefEntryComparator;
import net.sf.jabref.bibtex.comparator.FieldComparator;
import net.sf.jabref.bibtex.comparator.FieldComparatorStack;
import net.sf.jabref.groups.GroupTreeNode;
import net.sf.jabref.logic.config.SaveOrderConfig;
import net.sf.jabref.logic.id.IdComparator;
import net.sf.jabref.logic.util.strings.StringUtil;
import net.sf.jabref.migrations.VersionHandling;
import net.sf.jabref.model.database.BibDatabase;

public class BibDatabaseWriter {

    private static final Pattern REFERENCE_PATTERN = Pattern.compile("(#[A-Za-z]+#)"); // Used to detect string references in strings
    private static final Log LOGGER = LogFactory.getLog(BibDatabaseWriter.class);

    private static List<Comparator<BibEntry>> getSaveComparators(SavePreferences prefs, MetaData metaData) {

        /* three options:
         * 1. original order
         * 2. order specified in metaData
         * 3. order specified in preferences
         */

        List<Comparator<BibEntry>> comparators = new ArrayList<>();

        if (shouldSaveInOriginalOrder(prefs, metaData)) {
            // Take care, using CrossRefEntry-Comparator, that referred entries occur after referring
            // ones. Apart from crossref requirements, entries will be sorted based on their creation order,
            // utilizing the fact that IDs used for entries are increasing, sortable numbers.
            comparators = new ArrayList<>();
            comparators.add(new CrossRefEntryComparator());
            comparators.add(new IdComparator());
        } else {
            if (prefs.isSaveOperation()) {
                List<String> storedSaveOrderConfig = metaData.getData(
                        net.sf.jabref.gui.DatabasePropertiesDialog.SAVE_ORDER_CONFIG);

                if (storedSaveOrderConfig != null) {
                    // follow the metaData and overwrite provided sort settings
                    SaveOrderConfig saveOrderConfig = new SaveOrderConfig(storedSaveOrderConfig);
                    assert!saveOrderConfig.saveInOriginalOrder;
                    assert saveOrderConfig.saveInSpecifiedOrder;
                    prefs.pri = saveOrderConfig.sortCriteria[0].field;
                    prefs.sec = saveOrderConfig.sortCriteria[1].field;
                    prefs.ter = saveOrderConfig.sortCriteria[2].field;
                    prefs.priD = saveOrderConfig.sortCriteria[0].descending;
                    prefs.secD = saveOrderConfig.sortCriteria[1].descending;
                    prefs.terD = saveOrderConfig.sortCriteria[2].descending;
                }
            }

            if (prefs.isSaveOperation()) {
                comparators.add(new CrossRefEntryComparator());
            }
            comparators.add(new FieldComparator(prefs.pri, prefs.priD));
            comparators.add(new FieldComparator(prefs.sec, prefs.secD));
            comparators.add(new FieldComparator(prefs.ter, prefs.terD));
            comparators.add(new FieldComparator(BibEntry.KEY_FIELD));
        }

        return comparators;
    }

    /*
     * We have begun to use getSortedEntries() for both database save operations
     * and non-database save operations.  In a non-database save operation
     * (such as the exportDatabase call), we do not wish to use the
     * global preference of saving in standard order.
     */
    public static List<BibEntry> getSortedEntries(BibDatabaseContext bibDatabaseContext, Set<String> keySet,
            SavePreferences prefs) {

        //if no meta data are present, simply return in original order
        if (bibDatabaseContext.getMetaData() == null) {
            List<BibEntry> result = new LinkedList<>();
            result.addAll(bibDatabaseContext.getDatabase().getEntries());
            return result;
        }

        List<Comparator<BibEntry>> comparators = BibDatabaseWriter.getSaveComparators(prefs,
                bibDatabaseContext.getMetaData());
        FieldComparatorStack<BibEntry> comparatorStack = new FieldComparatorStack<>(comparators);

        List<BibEntry> sorted = new ArrayList<>();
        if (keySet == null) {
            keySet = bibDatabaseContext.getDatabase().getKeySet();
        }
        for (String id : keySet) {
            sorted.add(bibDatabaseContext.getDatabase().getEntryById(id));
        }
        Collections.sort(sorted, comparatorStack);

        return sorted;
    }

    private static boolean shouldSaveInOriginalOrder(SavePreferences prefs, MetaData metaData) {
        boolean inOriginalOrder;
        if (prefs.isSaveOperation()) {
            List<String> storedSaveOrderConfig = metaData.getData(
                    net.sf.jabref.gui.DatabasePropertiesDialog.SAVE_ORDER_CONFIG);
            if (storedSaveOrderConfig == null) {
                inOriginalOrder = true;
            } else {
                SaveOrderConfig saveOrderConfig = new SaveOrderConfig(storedSaveOrderConfig);
                inOriginalOrder = saveOrderConfig.saveInOriginalOrder;
            }
        } else {
            inOriginalOrder = Globals.prefs.getBoolean(JabRefPreferences.EXPORT_IN_ORIGINAL_ORDER);
        }
        return inOriginalOrder;
    }

    private BibtexString.Type previousStringType;

    public SaveSession saveDatabase(BibDatabaseContext bibDatabaseContext, SavePreferences prefs)
            throws SaveException {
        return saveDatabase(bibDatabaseContext, prefs, false, false);
    }

    /**
     * Saves the database to file. Two boolean values indicate whether only
     * entries with a nonzero Globals.SEARCH value and only entries with a
     * nonzero Globals.GROUPSEARCH value should be saved. This can be used to
     * let the user save only the results of a search. False and false means all
     * entries are saved.
     */
    public SaveSession saveDatabase(BibDatabaseContext bibDatabaseContext, SavePreferences prefs, boolean checkSearch,
            boolean checkGroup) throws SaveException {
        return savePartOfDatabase(bibDatabaseContext, bibDatabaseContext.getDatabase().getEntries(), prefs, checkSearch,
                checkGroup);
    }

    public SaveSession savePartOfDatabase(BibDatabaseContext bibDatabaseContext, Collection<BibEntry> entries,
            SavePreferences prefs, boolean checkSearch, boolean checkGroup)
                    throws SaveException {

        SaveSession session;
        try {
            session = new SaveSession(prefs.getEncoding(), prefs.getMakeBackup());
        } catch (IOException e) {
            throw new SaveException(e.getMessage(), e.getLocalizedMessage());
        }

        // Map to collect entry type definitions that we must save along with entries using them.
        Map<String, EntryType> typesToWrite = new TreeMap<>();

        BibEntry exceptionCause = null;
        // Get our data stream. This stream writes only to a temporary file until committed.
        try (VerifyingWriter writer = session.getWriter()) {

            if (prefs.getSaveType() != SavePreferences.DatabaseSaveType.PLAIN_BIBTEX) {
                // Write signature.
                writeBibFileHeader(writer, prefs.getEncoding());
            }

            // Write preamble if there is one.
            writePreamble(writer, bibDatabaseContext.getDatabase().getPreamble());

            // Write strings if there are any.
            writeStrings(writer, bibDatabaseContext.getDatabase());

            // Write database entries.
            List<BibEntry> sortedEntries = BibDatabaseWriter.getSortedEntries(bibDatabaseContext,
                    entries.stream().map(entry -> entry.getId()).collect(Collectors.toSet()), prefs);
            sortedEntries = BibDatabaseWriter.applySaveActions(sortedEntries, bibDatabaseContext.getMetaData());
            BibEntryWriter bibtexEntryWriter = new BibEntryWriter(new LatexFieldFormatter(), true);
            for (BibEntry entry : sortedEntries) {
                exceptionCause = entry;

                // Check if the entry should be written.
                boolean write = true;

                if (checkSearch && entry.isSearchHit()) {
                    write = false;
                }

                if (checkGroup && entry.isGroupHit()) {
                    write = false;
                }

                if (write) {
                    // Check if we must write the type definition for this
                    // entry, as well. Our criterion is that all non-standard
                    // types (*not* all customized standard types) must be written.
                    if (! EntryTypes.getStandardType(entry.getType(), bibDatabaseContext.getMode()).isPresent()) {
                        // If user-defined entry type, then add it
                        // Otherwise (getType returns empty optional) it is a completely unknown entry type, so ignore it
                        EntryTypes.getType(entry.getType(), bibDatabaseContext.getMode()).ifPresent(
                                entryType -> typesToWrite.put(entryType.getName(), entryType));
                    }

                    bibtexEntryWriter.write(entry, writer, bibDatabaseContext.getMode());

                  //only append newline if the entry has changed
                    if (!entry.hasChanged()) {
                        writer.write(Globals.NEWLINE);
                    }
                }
            }

            if (prefs.getSaveType() != SavePreferences.DatabaseSaveType.PLAIN_BIBTEX) {
                // Write meta data.
                writeMetaData(writer, bibDatabaseContext.getMetaData());

                // Write type definitions, if any:
                writeTypeDefinitions(writer, typesToWrite);
            }

            //finally write whatever remains of the file, but at least a concluding newline
            writeEpilog(writer, bibDatabaseContext.getDatabase());
        } catch (IOException ex) {
            LOGGER.error("Could not write file", ex);
            session.cancel();
            throw new SaveException(ex.getMessage(), ex.getLocalizedMessage(), exceptionCause);
        }

        return session;

    }

    /**
     * Saves the database to file, including only the entries included in the
     * supplied input array bes.
     */
    public SaveSession savePartOfDatabase(BibDatabaseContext bibDatabaseContext, SavePreferences prefs,
            Collection<BibEntry> entries) throws SaveException {

        return savePartOfDatabase(bibDatabaseContext, entries, prefs, false, false);
    }

    private static List<BibEntry> applySaveActions(List<BibEntry> toChange, MetaData metaData) {
        if (metaData.getData(SaveActions.META_KEY) == null) {
            // save actions defined -> apply for every entry
            List<BibEntry> result = new ArrayList<>(toChange.size());

            SaveActions saveActions = new SaveActions(metaData);

            for (BibEntry entry : toChange) {
                result.add(saveActions.applySaveActions(entry));
            }

            return result;
        } else {
            // no save actions defined -> do nothing
            return toChange;
        }
    }

    /**
     * Writes the file encoding information.
     *
     * @param encoding String the name of the encoding, which is part of the file header.
     */
    private void writeBibFileHeader(Writer out, Charset encoding) throws IOException {
        out.write("% ");
        out.write(Globals.encPrefix + encoding);
    }

    private void writeEpilog(VerifyingWriter writer, BibDatabase database) throws IOException {
        if ((database.getEpilog() != null) && !(database.getEpilog().isEmpty())) {
           writer.write(database.getEpilog());
        } else {
           writer.write(Globals.NEWLINE);
        }
    }

    /**
     * Writes all data to the specified writer, using each object's toString() method.
     */
    private void writeMetaData(Writer out, MetaData metaData) throws IOException {
        if (metaData == null) {
            return;
        }

        // first write all meta data except groups
        for (String key : metaData) {

            StringBuffer sb = new StringBuffer();
            sb.append(Globals.NEWLINE);
            sb.append(Globals.NEWLINE);
            List<String> orderedData = metaData.getData(key);
            sb.append("@comment{").append(MetaData.META_FLAG).append(key).append(":");
            for (int j = 0; j < orderedData.size(); j++) {
                sb.append(StringUtil.quote(orderedData.get(j), ";", '\\')).append(";");
            }
            sb.append("}");

            out.write(sb.toString());
        }
        // write groups if present. skip this if only the root node exists
        // (which is always the AllEntriesGroup).
        GroupTreeNode groupsRoot = metaData.getGroups();
        if ((groupsRoot != null) && (groupsRoot.getChildCount() > 0)) {
            StringBuffer sb = new StringBuffer();
            // write version first
            sb.append(Globals.NEWLINE);
            sb.append(Globals.NEWLINE);
            sb.append("@comment{").append(MetaData.META_FLAG).append("groupsversion:");
            sb.append("" + VersionHandling.CURRENT_VERSION + ";");
            sb.append("}");

            out.write(sb.toString());

            // now write actual groups
            sb = new StringBuffer();
            sb.append(Globals.NEWLINE);
            sb.append(Globals.NEWLINE);
            sb.append("@comment{").append(MetaData.META_FLAG).append("groupstree:");
            sb.append(Globals.NEWLINE);
            // GroupsTreeNode.toString() uses "\n" for separation
            StringTokenizer tok = new StringTokenizer(groupsRoot.getTreeAsString(), Globals.NEWLINE);
            while (tok.hasMoreTokens()) {
                StringBuffer s = new StringBuffer(StringUtil.quote(tok.nextToken(), ";", '\\') + ";");
                sb.append(s);
                sb.append(Globals.NEWLINE);
            }
            sb.append("}");
            out.write(sb.toString());
        }
    }

    private void writePreamble(Writer fw, String preamble) throws IOException {
        if (preamble != null) {
            fw.write("@PREAMBLE{");
            fw.write(preamble);
            fw.write('}' + Globals.NEWLINE + Globals.NEWLINE);
        }
    }

    private void writeString(Writer fw, BibtexString bs, Map<String, BibtexString> remaining, int maxKeyLength)
            throws IOException {
        // First remove this from the "remaining" list so it can't cause problem with circular refs:
        remaining.remove(bs.getName());

        //if the string has not been modified, write it back as it was
        if (!bs.hasChanged()) {
            fw.write(bs.getParsedSerialization());
            return;
        }

        // Then we go through the string looking for references to other strings. If we find references
        // to strings that we will write, but still haven't, we write those before proceeding. This ensures
        // that the string order will be acceptable for BibTeX.
        String content = bs.getContent();
        Matcher m;
        while ((m = BibDatabaseWriter.REFERENCE_PATTERN.matcher(content)).find()) {
            String foundLabel = m.group(1);
            int restIndex = content.indexOf(foundLabel) + foundLabel.length();
            content = content.substring(restIndex);
            Object referred = remaining.get(foundLabel.substring(1, foundLabel.length() - 1));
            // If the label we found exists as a key in the "remaining" Map, we go on and write it now:
            if (referred != null) {
                writeString(fw, (BibtexString) referred, remaining, maxKeyLength);
            }
        }

        if (previousStringType != bs.getType()) {
            fw.write(Globals.NEWLINE);
            previousStringType = bs.getType();
        }

        StringBuilder suffixSB = new StringBuilder();
        for (int i = maxKeyLength - bs.getName().length(); i > 0; i--) {
            suffixSB.append(' ');
        }
        String suffix = suffixSB.toString();

        fw.write("@String { " + bs.getName() + suffix + " = ");
        if (bs.getContent().isEmpty()) {
            fw.write("{}");
        } else {
            try {
                String formatted = new LatexFieldFormatter().format(bs.getContent(), LatexFieldFormatter.BIBTEX_STRING);
                fw.write(formatted);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "The # character is not allowed in BibTeX strings unless escaped as in '\\#'.\n"
                                + "Before saving, please edit any strings containing the # character.");
            }
        }

        fw.write(" }" + Globals.NEWLINE);
    }

    /**
     * Write all strings in alphabetical order, modified to produce a safe (for
     * BibTeX) order of the strings if they reference each other.
     *
     * @param fw       The Writer to send the output to.
     * @param database The database whose strings we should write.
     * @throws IOException If anthing goes wrong in writing.
     */
    private void writeStrings(Writer fw, BibDatabase database) throws IOException {
        previousStringType = BibtexString.Type.AUTHOR;
        List<BibtexString> strings = new ArrayList<>();
        for (String s : database.getStringKeySet()) {
            strings.add(database.getString(s));
        }
        strings.sort(new BibtexStringComparator(true));
        // First, make a Map of all entries:
        HashMap<String, BibtexString> remaining = new HashMap<>();
        int maxKeyLength = 0;
        for (BibtexString string : strings) {
            remaining.put(string.getName(), string);
            maxKeyLength = Math.max(maxKeyLength, string.getName().length());
        }

        for (BibtexString.Type t : BibtexString.Type.values()) {
            for (BibtexString bs : strings) {
                if (remaining.containsKey(bs.getName()) && (bs.getType() == t)) {
                    writeString(fw, bs, remaining, maxKeyLength);
                }
            }
        }
    }

    private void writeTypeDefinitions(VerifyingWriter writer, Map<String, EntryType> types) throws IOException {
        if (!types.isEmpty()) {
            for (Map.Entry<String, EntryType> stringBibtexEntryTypeEntry : types.entrySet()) {
                EntryType type = stringBibtexEntryTypeEntry.getValue();
                if (type instanceof CustomEntryType) {
                    CustomEntryType customType = (CustomEntryType) type;
                    CustomEntryTypesManager.save(customType, writer);
                    writer.write(Globals.NEWLINE);
                }
            }
        }
    }

}
