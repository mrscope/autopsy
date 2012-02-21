/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * KeywordSearchListsViewerPanel.java
 *
 * Created on Feb 17, 2012, 1:45:38 PM
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 *
 * @author dfickling
 */
public class KeywordSearchListsViewerPanel extends AbstractKeywordSearchPerformer {
    
    private static final Logger logger = Logger.getLogger(KeywordSearchListsViewerPanel.class.getName());
    private static KeywordSearchListsViewerPanel instance;
    private KeywordSearchListsXML loader;
    private KeywordListsTableModel listsTableModel;
    private KeywordsTableModel keywordsTableModel;
    private ActionListener ingestListener;
    private ActionListener searchListener;
    private boolean ingestRunning;

    /** Creates new form KeywordSearchListsViewerPanel */
    private KeywordSearchListsViewerPanel() {
        listsTableModel = new KeywordListsTableModel();
        keywordsTableModel = new KeywordsTableModel();
        initComponents();
        customizeComponents();
    }
    
    public static KeywordSearchListsViewerPanel getDefault() {
        if (instance == null)
            return new KeywordSearchListsViewerPanel();
        else
            return instance;
    }
    
    private void customizeComponents() {
        listsTableModel.resync();
        
        listsTable.setTableHeader(null);
        //customize column witdhs
        final int leftWidth = leftPane.getPreferredSize().width;
        TableColumn column = null;
        for (int i = 0; i < listsTable.getColumnCount(); i++) {
            column = listsTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (leftWidth * 0.10)));
            } else {
                column.setPreferredWidth(((int) (leftWidth * 0.89)));
            }
        }
        final int rightWidth = rightPane.getPreferredSize().width;
        for (int i = 0; i < keywordsTable.getColumnCount(); i++) {
            column = keywordsTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (rightWidth * 0.84)));
            } else {
                column.setPreferredWidth(((int) (rightWidth * 0.15)));
            }
        }
        
        loader = KeywordSearchListsXML.getCurrent();
        loader.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String changed = evt.getPropertyName();
                Object oldValue = evt.getOldValue();
                Object newValue = evt.getNewValue();
                
                if (changed.equals(KeywordSearchListsXML.ListsEvt.LIST_ADDED.toString())) {
                    listsTableModel.resync();
                } else if (changed.equals(KeywordSearchListsXML.ListsEvt.LIST_DELETED.toString())) {
                    listsTableModel.resync();
                    if(listsTable.getRowCount() > 0)
                        listsTable.getSelectionModel().setSelectionInterval(0, 0);
                    else
                        listsTable.getSelectionModel().clearSelection();
                } else if (changed.equals(KeywordSearchListsXML.ListsEvt.LIST_UPDATED.toString())) {
                    listsTableModel.resync();
                }
            }
        });
        listsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    KeywordSearchList list = listsTableModel.getListAt(index);
                    keywordsTableModel.resync(list);
                } else {
                    keywordsTableModel.deleteAll();
                }
            }
        });
        ingestListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addToIngestAction(e);
            }
        };
        searchListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchAction(e);
            }
        };
        
        if(IngestManager.getDefault().isFileIngestRunning())
            initIngest(0);
        else
            initIngest(1);
        
        IngestManager.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String changed = evt.getPropertyName();
                Object oldValue = evt.getOldValue();
                if(changed.equals(IngestManager.SERVICE_COMPLETED_EVT) &&
                        ((String) oldValue).equals(KeywordSearchIngestService.MODULE_NAME))
                    initIngest(1);
                else if(changed.equals(IngestManager.SERVICE_STARTED_EVT) &&
                        ((String) oldValue).equals(KeywordSearchIngestService.MODULE_NAME))
                    initIngest(0);
            }
            
        });
    }
    
    /** 
     * Initialize this panel depending on whether ingest is running
     * @param running 
     * case 0: ingest running
     * case 1: ingest not running
     * case 2: ingest completed
     * case 3: ingest started
     */
    private void initIngest(int running) {
        ActionListener[] current = searchAddButton.getActionListeners();
        for(int i = 0; i < current.length; i++) {
            if(current[i].equals(ingestListener) || current[i].equals(searchListener))
                searchAddButton.removeActionListener(current[i]);
        }
        switch (running) {
            case 0:
                ingestRunning = true;
                searchAddButton.setText("Add to Ingest");
                searchAddButton.addActionListener(ingestListener);
                listsTableModel.resync();
                break;
            case 1:
                ingestRunning = false;
                searchAddButton.setText("Search");
                searchAddButton.addActionListener(searchListener);
                listsTableModel.resync();
                break;
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        leftPane = new javax.swing.JScrollPane();
        listsTable = new javax.swing.JTable();
        rightPane = new javax.swing.JScrollPane();
        keywordsTable = new javax.swing.JTable();
        manageListsButton = new javax.swing.JButton();
        searchAddButton = new javax.swing.JButton();

        leftPane.setBackground(new java.awt.Color(204, 204, 204));
        leftPane.setMinimumSize(new java.awt.Dimension(150, 23));

        listsTable.setBackground(new java.awt.Color(240, 240, 240));
        listsTable.setModel(listsTableModel);
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);
        listsTable.getTableHeader().setReorderingAllowed(false);
        leftPane.setViewportView(listsTable);

        jSplitPane1.setLeftComponent(leftPane);

        keywordsTable.setBackground(new java.awt.Color(240, 240, 240));
        keywordsTable.setModel(keywordsTableModel);
        keywordsTable.setShowHorizontalLines(false);
        keywordsTable.setShowVerticalLines(false);
        rightPane.setViewportView(keywordsTable);

        jSplitPane1.setRightComponent(rightPane);

        manageListsButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchListsViewerPanel.class, "KeywordSearchListsViewerPanel.manageListsButton.text")); // NOI18N
        manageListsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageListsButtonActionPerformed(evt);
            }
        });

        searchAddButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchListsViewerPanel.class, "KeywordSearchListsViewerPanel.searchAddButton.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(searchAddButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 220, Short.MAX_VALUE)
                .addComponent(manageListsButton)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 268, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(manageListsButton)
                    .addComponent(searchAddButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void manageListsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageListsButtonActionPerformed
        SystemAction.get(KeywordSearchConfigurationAction.class).performAction();
    }//GEN-LAST:event_manageListsButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JTable keywordsTable;
    private javax.swing.JScrollPane leftPane;
    private javax.swing.JTable listsTable;
    private javax.swing.JButton manageListsButton;
    private javax.swing.JScrollPane rightPane;
    private javax.swing.JButton searchAddButton;
    // End of variables declaration//GEN-END:variables

    
    private void searchAction(ActionEvent e) {
        if (filesIndexed == 0)
            return;

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            search();
        } finally {
            setCursor(null);
        }
    }
    
    private void addToIngestAction(ActionEvent e) {
        //TODO: something
    }
    
    @Override
    public List<Keyword> getQueryList() {
        List<Keyword> ret = new ArrayList<Keyword>();
        for(KeywordSearchList list : getSelectedLists()) {
            ret.addAll(list.getKeywords());
        }
        return ret;
    }
    
    private List<KeywordSearchList> getSelectedLists() {
        return listsTableModel.getSelectedListsL();
    }

    @Override
    public boolean isMultiwordQuery() {
        return true;
    }

    @Override
    public boolean isLuceneQuerySelected() {
        throw new UnsupportedOperationException("Not supported for multi-word queries.");
    }

    @Override
    public String getQueryText() {
        throw new UnsupportedOperationException("Not supported for multi-word queries.");
    }
    
    void addSearchButtonActionListener(ActionListener al) {
        searchAddButton.addActionListener(al);
    }
    
    private class KeywordListsTableModel extends AbstractTableModel {
        //data

        private KeywordSearchListsXML listsHandle = KeywordSearchListsXML.getCurrent();
        private Set<ListTableEntry> listData = new TreeSet<ListTableEntry>();

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public int getRowCount() {
            return listData.size();
        }

        @Override
        public String getColumnName(int column) {
            String ret = null;
            switch(column) {
                case 0:
                    ret = "Selected";
                    break;
                case 1:
                    ret = "Name";
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            ListTableEntry entry = null;
            //iterate until row
            Iterator<ListTableEntry> it = listData.iterator();
            for (int i = 0; i <= rowIndex; ++i) {
                entry = it.next();
            }
            switch (columnIndex) {
                case 0:
                    ret = (Object) entry.selected;
                    break;
                case 1:
                    ret = (Object) entry.name;
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if(columnIndex == 0){
                ListTableEntry entry = null;
                Iterator<ListTableEntry> it = listData.iterator();
                for(int i = 0; i <= rowIndex; i++) {
                    entry = it.next();
                }
                if(entry != null) {
                    entry.selected = (Boolean) aValue;
                    if(ingestRunning) {
                        updateUseForIngest(getListAt(rowIndex), (Boolean) aValue);
                    }
                }
                    
            }
        }

        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
        
        private void updateUseForIngest(KeywordSearchList list, boolean selected) {
            listsHandle.addList(list.getName(), list.getKeywords(), selected);
        }

        List<String> getAllLists() {
            List<String> ret = new ArrayList<String>();
            for (ListTableEntry e : listData) {
                ret.add(e.name);
            }
            return ret;
        }
        
        KeywordSearchList getListAt(int rowIndex) {
            return listsHandle.getList((String)getValueAt(rowIndex, 1));
        }

        List<String> getSelectedLists() {
            List<String> ret = new ArrayList<String>();
            for(ListTableEntry e : listData) {
                if(e.selected)
                    ret.add(e.name);
            }
            return ret;
        }
        
        List<KeywordSearchList> getSelectedListsL() {
            List<KeywordSearchList> ret = new ArrayList<KeywordSearchList>();
            for(String s : getSelectedLists()) {
                ret.add(listsHandle.getList(s));
            }
            return ret;
        }

        boolean listExists(String list) {
            List<String> all = getAllLists();
            return all.contains(list);
        }

        //resync model from handle, then update table
        void resync() {
            listData.clear();
            addLists(listsHandle.getListsL());
            fireTableDataChanged();
        }

        //resync single model entry from handle, then update table
        void resync(String listName) {
            ListTableEntry found = null;
            for (ListTableEntry e : listData) {
                if (e.name.equals(listName)) {
                    found = e;
                    break;
                }
            }
            if (found != null) {
                listData.remove(found);
                addList(listsHandle.getList(listName));
            }
            fireTableDataChanged();
        }

        //add list to the model
        private void addList(KeywordSearchList list) {
            if (!listExists(list.getName())) {
                listData.add(new ListTableEntry(list, ingestRunning));
            }
        }

        //add lists to the model
        private void addLists(List<KeywordSearchList> lists) {
            for (KeywordSearchList list : lists) {
                if (!listExists(list.getName())) {
                    listData.add(new ListTableEntry(list, ingestRunning));
                }
            }
        }

        //single model entry
        private class ListTableEntry implements Comparable {

            String name;
            Boolean selected;
            
            ListTableEntry(KeywordSearchList list, boolean ingestRunning) {
                this.name = list.getName();
                if(ingestRunning)
                    this.selected = list.getUseForIngest();
                else
                    this.selected = false;
            }

            @Override
            public int compareTo(Object o) {
                return this.name.compareTo(((ListTableEntry) o).name);
            }
        }
    }
    
    private class KeywordsTableModel extends AbstractTableModel {

        Set<KeywordTableEntry> listData = new TreeSet<KeywordTableEntry>();
        
        @Override
        public int getRowCount() {
            return listData.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            String ret = null;
            switch(column) {
                case 0:
                    ret = "Name";
                    break;
                case 1:
                    ret = "RegEx";
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            KeywordTableEntry entry = null;
            //iterate until row
            Iterator<KeywordTableEntry> it = listData.iterator();
            for (int i = 0; i <= rowIndex; ++i) {
                entry = it.next();
            }
            switch (columnIndex) {
                case 0:
                    ret = (Object) entry.name;
                    break;
                case 1:
                    ret = (Object) entry.regex;
                    break;
                default:
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }
        
        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
        
        void resync(KeywordSearchList list) {
            listData.clear();
            for(Keyword k : list.getKeywords()) {
                listData.add(new KeywordTableEntry(k));
            }
            fireTableDataChanged();
        }
        
        void deleteAll() {
            listData.clear();
            fireTableDataChanged();
        }
        
        //single model entry
        private class KeywordTableEntry implements Comparable {

            String name;
            Boolean regex;

            KeywordTableEntry(Keyword keyword) {
                this.name = keyword.getQuery();
                this.regex = !keyword.isLiteral();
            }

            @Override
            public int compareTo(Object o) {
                return this.name.compareTo(((KeywordTableEntry) o).name);
            }
        }
    }
    
    
}