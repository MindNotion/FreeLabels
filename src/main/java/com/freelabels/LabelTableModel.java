package com.freelabels;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/** Editable table showing all imported/custom columns without truncating the schema to nine fields. */
public class LabelTableModel extends AbstractTableModel {
    private final List<Label> labels;
    public LabelTableModel(List<Label> labels){this.labels=labels;}
    private int maxFields(){int n=0;for(Label l:labels)n=Math.max(n,l.customFields.size());return n;}
    @Override public int getRowCount(){return labels.size();}
    @Override public int getColumnCount(){return 1+maxFields();}
    @Override public String getColumnName(int c){return c==0?"#":"Field "+c;}
    @Override public Object getValueAt(int r,int c){
        if(c==0)return r+1;
        Label l=labels.get(r); int i=c-1;
        if(i>=l.customFields.size())return "";
        Label.CustomField f=l.customFields.get(i);
        String prefix=f.type==Label.CustomField.Type.BARCODE?"barcode=":f.type==Label.CustomField.Type.QR?"qr_code=":"";
        return prefix+f.value;
    }
    @Override public boolean isCellEditable(int r,int c){return c>0;}
    @Override public void setValueAt(Object value,int r,int c){
        if(c==0)return; Label l=labels.get(r);int i=c-1;
        while(l.customFields.size()<=i)l.customFields.add(new Label.CustomField("",Label.CustomField.Type.TEXT));
        String v=value==null?"":value.toString();Label.CustomField.Type t=Label.CustomField.Type.TEXT;
        if(v.regionMatches(true,0,"barcode=",0,8)){t=Label.CustomField.Type.BARCODE;v=v.substring(8);}
        else if(v.regionMatches(true,0,"qr_code=",0,8)){t=Label.CustomField.Type.QR;v=v.substring(8);}
        l.customFields.set(i,new Label.CustomField(v,t));fireTableCellUpdated(r,c);
    }
    public void removeRow(int r){labels.remove(r);fireTableDataChanged();}
    public void refresh(){fireTableStructureChanged();}
}
