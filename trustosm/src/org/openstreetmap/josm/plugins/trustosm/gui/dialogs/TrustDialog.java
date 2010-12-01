package org.openstreetmap.josm.plugins.trustosm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.DefaultNameFormatter;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.plugins.trustosm.TrustOSMplugin;
import org.openstreetmap.josm.plugins.trustosm.data.TrustOSMItem;
import org.openstreetmap.josm.plugins.trustosm.data.TrustSignatures;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class TrustDialog extends ToggleDialog implements ActionListener, SelectionChangedListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = -3324984194315776740L;


	public final static Color BGCOLOR_NO_SIG = new Color(234, 234, 234);
	//	public final static Color BGCOLOR_VALID_SIG = new Color(235,255,177);
	public final static Color BGCOLOR_VALID_SIG = new Color(74,245,106);
	public final static Color BGCOLOR_BROKEN_SIG = new Color(255, 197, 197);
	public final static Color BGCOLOR_REMOVED_ITEM = new Color(255, 100, 100);
	public final static Color BGCOLOR_UPDATED_ITEM = new Color(249,221,95);


	/** Use a TrustGPGPreparer to sign or validate signatures */
	//private final TrustGPGPreparer gpg;

	/** The check signatures button */
	private final SideButton checkButton;

	/** The sign button */
	private final SideButton signButton;

	/** The show sigs button */
	private final SideButton showButton;

	private final Map<String, Byte> rowStatus = new HashMap<String, Byte>();

	/** The selected osmData */
	private Collection<? extends OsmPrimitive> osmData;

	private final JTree geomTree;

	/**
	 * The property data.
	 */
	private final DefaultTableModel propertyData = new DefaultTableModel() {
		/**
		 * 
		 */
		private static final long serialVersionUID = -1252801283184909691L;
		@Override public boolean isCellEditable(int row, int column) {
			return false;
		}
		@Override public Class<?> getColumnClass(int columnIndex) {
			return String.class;
		}
	};
	private final JTable propertyTable = new JTable(propertyData) {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		@Override
		public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
			Component c = super.prepareRenderer(renderer, row, column);
			Byte stat = rowStatus.get(getModel().getValueAt(row, 0));
			if (!isRowSelected(row))
				switch (stat.byteValue()) {
				case -2: c.setBackground( BGCOLOR_REMOVED_ITEM ); break;
				case -1: c.setBackground( BGCOLOR_BROKEN_SIG ); break;
				case 1: c.setBackground( BGCOLOR_VALID_SIG ); break;
				default: c.setBackground( BGCOLOR_NO_SIG ); break;
				}
			return c;
		}
	};

	/**
	 * Constructor
	 */
	public TrustDialog() {
		super(tr("Object signatures"), "trustosm", tr("Open object signing window."),
				Shortcut.registerShortcut("subwindow:trustosm", tr("Toggle: {0}", tr("Object signatures")),
						KeyEvent.VK_T, Shortcut.GROUP_LAYER, Shortcut.SHIFT_DEFAULT), 150);


		// setting up the properties table
		propertyData.setColumnIdentifiers(new String[]{tr("Key"),tr("Value")});

		// copy and paste from org.openstreetmap.josm.gui.dialogs.properties.PropertiesDialog

		propertyTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer(){
			/**
			 * 
			 */
			private static final long serialVersionUID = 8003207668070727861L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value,
					boolean isSelected, boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
				if (c instanceof JLabel) {
					String str = null;
					if (value instanceof String) {
						str = (String) value;
					} else if (value instanceof Map<?, ?>) {
						Map<?, ?> v = (Map<?, ?>) value;
						if (v.size() != 1) {
							str=tr("<different>");
							c.setFont(c.getFont().deriveFont(Font.ITALIC));
						} else {
							final Map.Entry<?, ?> entry = v.entrySet().iterator().next();
							str = (String) entry.getKey();
						}
					}
					((JLabel)c).setText(str);
				}
				return c;
			}
		});

		geomTree = new JTree( createTree() );

		geomTree.setBackground( BGCOLOR_NO_SIG );
		geomTree.setRootVisible(false);
		geomTree.setCellRenderer(new DefaultTreeCellRenderer(){

			/**
			 * 
			 */
			private static final long serialVersionUID = -3070210847060314196L;

			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value,
					boolean selected, boolean expanded, boolean leaf, int row,
					boolean hasFocus)
			{
				super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

				DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
				if (node.isRoot()) return this;
				OsmPrimitive osm = (OsmPrimitive) node.getUserObject();
				setIcon(ImageProvider.get(OsmPrimitiveType.from(osm)));
				setText(osm.getDisplayName(DefaultNameFormatter.getInstance()));

				if (osm instanceof Node) {
					Node osmNode = (Node) osm;
					if (((DefaultMutableTreeNode) node.getParent()).getUserObject() instanceof Way) {
						osm = (OsmPrimitive) ((DefaultMutableTreeNode) node.getParent()).getUserObject();
					}
					TrustSignatures sigs;
					String id = String.valueOf(osm.getUniqueId());
					if (TrustOSMplugin.signedItems.containsKey(id) && (sigs = TrustOSMplugin.signedItems.get(id).getSigsOnNode(osmNode)) != null) {
						byte stat = sigs.getStatus();
						switch (stat) {
						case -2: setBackgroundNonSelectionColor( BGCOLOR_REMOVED_ITEM ); break;
						case -1: setBackgroundNonSelectionColor( BGCOLOR_BROKEN_SIG ); break;
						case 1: setBackgroundNonSelectionColor( BGCOLOR_VALID_SIG ); break;
						default: setBackgroundNonSelectionColor( BGCOLOR_NO_SIG ); break;
						}
					} else {
						setBackgroundNonSelectionColor( BGCOLOR_NO_SIG );
					}
				} else if (osm instanceof Way) {
					setBackgroundNonSelectionColor( BGCOLOR_NO_SIG );
				}
				return this;
			}


		});




		JPanel dataPanel = new JPanel();
		dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.PAGE_AXIS));
		propertyTable.setAlignmentX(LEFT_ALIGNMENT);
		dataPanel.add(propertyTable);
		geomTree.setAlignmentX(LEFT_ALIGNMENT);
		dataPanel.add(geomTree);

		add(new JScrollPane(dataPanel), BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new GridLayout(1, 3));

		checkButton = new SideButton(marktr("Check"), "checksignatures", "TrustOSM",
				tr("Check all available signatures for selected object."), this);
		buttonPanel.add(checkButton);

		signButton = new SideButton(marktr("Sign"), "sign", "TrustOSM",
				tr("Digital sign selected Tags, if you believe they are correct."), this);
		buttonPanel.add(signButton);

		showButton = new SideButton(marktr("Show"), "showsig", "TrustOSM",
				tr("Show all available signatures for selected attribute."), this);
		buttonPanel.add(showButton);


		add(buttonPanel, BorderLayout.SOUTH);
		DataSet.addSelectionListener(this);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String actionCommand = e.getActionCommand();
		if (actionCommand.equals("Check")) {
			for (OsmPrimitive osm : osmData) {
				String id = String.valueOf(osm.getUniqueId());
				if (TrustOSMplugin.signedItems.containsKey(id))
					TrustOSMplugin.gpg.checkAll(TrustOSMplugin.signedItems.get(id));
				//checkedItems.put(osm, TrustOSMplugin.gpg.check(checkedItems.containsKey(osm)? checkedItems.get(osm) : new TrustOSMItem(osm)));
			}
			updateTable();
			geomTree.repaint();
		} else if (actionCommand.equals("Sign")) {
			for (int i : propertyTable.getSelectedRows()) {
				String key = (String)propertyTable.getValueAt(i, 0);
				for (OsmPrimitive osm : osmData) {
					if (osm.keySet().contains(key)) {
						String id = String.valueOf(osm.getUniqueId());
						TrustOSMplugin.signedItems.put(id, TrustOSMplugin.gpg.signTag(TrustOSMplugin.signedItems.containsKey(id)? TrustOSMplugin.signedItems.get(id) : new TrustOSMItem(osm), key));
					}
				}
			}
			if (geomTree.getSelectionPaths()!=null)
				for (TreePath tp : geomTree.getSelectionPaths()) {
					OsmPrimitive osm = (OsmPrimitive) ((DefaultMutableTreeNode) tp.getLastPathComponent()).getUserObject();
					String id = String.valueOf(osm.getUniqueId());
					if (osm instanceof Node) {
						TreePath parentPath = tp.getParentPath();
						if (geomTree.isPathSelected(parentPath)) return;

						Node osmNode = ((Node) osm);
						if (((DefaultMutableTreeNode) parentPath.getLastPathComponent()).getUserObject() instanceof Way) {
							osm = (OsmPrimitive) ((DefaultMutableTreeNode) parentPath.getLastPathComponent()).getUserObject();
						}
						TrustOSMItem trust = TrustOSMplugin.signedItems.containsKey(id)? TrustOSMplugin.signedItems.get(id) : new TrustOSMItem(osm);
						trust.storeNodeSig(osmNode, TrustOSMplugin.gpg.signNode(osm,osmNode));
						TrustOSMplugin.signedItems.put(id, trust);
					} else if (osm instanceof Way) {
						TrustOSMplugin.signedItems.put(id, TrustOSMplugin.gpg.signGeometry(TrustOSMplugin.signedItems.containsKey(id)? TrustOSMplugin.signedItems.get(id) : new TrustOSMItem(osm)));
					}
				}
			updateTable();
			geomTree.repaint();
		} else if (actionCommand.equals("Show")) {
			for (int i : propertyTable.getSelectedRows()) {
				String key = (String)propertyTable.getValueAt(i, 0);
				for (OsmPrimitive osm : osmData) {
					String id = String.valueOf(osm.getUniqueId());
					if (osm.keySet().contains(key) && TrustOSMplugin.signedItems.containsKey(id)) {
						TrustSignaturesDialog.showSignaturesDialog(TrustOSMplugin.signedItems.get(id), key);
					}
				}
			}
		}
	}
	/*
	public void showSignaturesDialog(TrustOSMItem trust, String key) {
		TrustSignatures sigs;
		if ((sigs = trust.getSigsOnKey(key)) == null) {
			JOptionPane.showMessageDialog(null,tr("Sorry, there are no Signatures for the selected Attribute."), tr("No Signature found"), JOptionPane.WARNING_MESSAGE);
		} else {
			JPanel p = new JPanel();
			p.setLayout(new BoxLayout(p, BoxLayout.PAGE_AXIS));
			Dimension d = new Dimension(0,20);
			JLabel head = new JLabel(tr("Selected key value pair was:\n{0}={1}",key,trust.getOsmItem().get(key)));
			head.setAlignmentX(LEFT_ALIGNMENT);
			p.add(head);
			p.add(Box.createRigidArea(d));
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd:hh.mm.ss");
			for (PGPSignature s : sigs.getSignatures()) {
				JTextArea sigtext = new JTextArea(sigs.getArmoredFulltextSignature(s));
				sigtext.setEditable(false);
				sigtext.setAlignmentX(LEFT_ALIGNMENT);
				p.add(sigtext);
				JLabel siginfo = new JLabel(tr("Signature created at {0} by User {1}",formatter.format(s.getCreationTime()),s.getHashedSubPackets().getSignerUserID()));
				siginfo.setAlignmentX(LEFT_ALIGNMENT);
				p.add(siginfo);
				p.add(Box.createRigidArea(d));
			}

			JScrollPane scroller = new JScrollPane(p);
			JPanel content = new JPanel();
			content.setMaximumSize(new Dimension(600,500));
			content.add(scroller);
			JOptionPane.showMessageDialog(Main.parent,content, tr("Clearsigned Signature"), JOptionPane.PLAIN_MESSAGE);
		}
	}
	 */
	private DefaultTreeModel createTree(){
		DefaultMutableTreeNode root = new DefaultMutableTreeNode();
		DefaultMutableTreeNode wayNode;
		if (osmData!=null)
			for (OsmPrimitive osm : osmData) {
				String id = String.valueOf(osm.getUniqueId());
				if(osm instanceof Node) {
					root.add(new DefaultMutableTreeNode(osm));
				} else if(osm instanceof Way) {
					wayNode = new DefaultMutableTreeNode(osm);
					List<Node> presentNodes = ((Way)osm).getNodes();
					Iterator<Node> iter = presentNodes.iterator();
					while (iter.hasNext()) {
						wayNode.add(new DefaultMutableTreeNode(iter.next()));
					}

					if (TrustOSMplugin.signedItems.containsKey(id)) {
						TrustOSMItem trust = TrustOSMplugin.signedItems.get(id);
						HashSet<Node> signedNodes = new HashSet<Node>(trust.getGeomSigs().keySet());
						signedNodes.removeAll(presentNodes);
						iter = signedNodes.iterator();
						Node removedNode;
						while (iter.hasNext()) {
							removedNode = iter.next();
							trust.updateNodeSigStatus(removedNode, TrustSignatures.ITEM_REMOVED);
							wayNode.add(new DefaultMutableTreeNode(removedNode));
						}
					}
					root.add(wayNode);
				} else if(osm instanceof Relation) {

				}

			}

		return new DefaultTreeModel(root);

	}

	public void updateTable() {
		// re-load property data
		propertyData.setRowCount(0);

		Map<String, Map<String, Integer>> valueCount = new TreeMap<String, Map<String, Integer>>();

		TrustOSMItem trust;

		valueCount.clear();
		rowStatus.clear();
		boolean sigsAvailable = false;

		for (OsmPrimitive osm : osmData) {
			String id = String.valueOf(osm.getUniqueId());
			if (TrustOSMplugin.signedItems.containsKey(id)) {
				trust = TrustOSMplugin.signedItems.get(id);
				sigsAvailable = true;
				Map<String,String> tags = osm.getKeys();
				Map<String, TrustSignatures>  signedTags = trust.getTagSigs();
				HashSet<String> removedKeys = new HashSet<String>(signedTags.keySet());
				removedKeys.removeAll(tags.keySet());
				for (String removedKey: removedKeys) {
					TrustSignatures sigs = signedTags.get(removedKey);
					sigs.setStatus( TrustSignatures.ITEM_REMOVED );
					//tags.putAll(TrustOSMplugin.gpg.getKeyValueFromSignature(sigs.getLatestSignature()));
				}

			} else {
				trust = new TrustOSMItem(osm);
				sigsAvailable = false;
			}

			//		trust = TrustOSMplugin.signedItems.containsKey(osm) ? TrustOSMplugin.signedItems.get(osm) : new TrustOSMItem(osm);

			for (String key: osm.keySet()) {
				String value = osm.get(key);
				//keyCount.put(key, keyCount.containsKey(key) ? keyCount.get(key) + 1 : 1);

				byte status = sigsAvailable && trust.getTagSigs().containsKey(key) ? trust.getTagSigs().get(key).getStatus() : TrustSignatures.SIG_UNKNOWN ;
				Byte oldstatus = rowStatus.containsKey(key)? rowStatus.get(key) : new Byte(TrustSignatures.SIG_VALID);
				Byte sigstatus = new Byte(status);
				Byte newstatus;
				if (sigstatus.equals(new Byte(TrustSignatures.SIG_BROKEN)) || oldstatus.equals(new Byte(TrustSignatures.SIG_BROKEN))) {
					newstatus = new Byte(TrustSignatures.SIG_BROKEN);
				} else if (sigstatus.equals(new Byte(TrustSignatures.SIG_UNKNOWN)) || oldstatus.equals(new Byte(TrustSignatures.SIG_UNKNOWN))) {
					newstatus = new Byte(TrustSignatures.SIG_UNKNOWN);
				} else newstatus = new Byte(TrustSignatures.SIG_VALID);

				rowStatus.put(key, newstatus );
				if (valueCount.containsKey(key)) {
					Map<String, Integer> v = valueCount.get(key);
					v.put(value, v.containsKey(value)? v.get(value) + 1 : 1 );
				} else {
					TreeMap<String,Integer> v = new TreeMap<String, Integer>();
					v.put(value, 1);
					valueCount.put(key, v);
				}
			}
		}
		for (Entry<String, Map<String, Integer>> e : valueCount.entrySet()) {
			int count=0;
			for (Entry<String, Integer> e1: e.getValue().entrySet()) {
				count+=e1.getValue();
			}
			if (count < osmData.size()) {
				e.getValue().put("", osmData.size()-count);
			}
			propertyData.addRow(new Object[]{e.getKey(), e.getValue()});
		}


		boolean hasSelection = !osmData.isEmpty();
		boolean hasTags = hasSelection && propertyData.getRowCount() > 0;

		propertyTable.setVisible(hasTags);
		propertyTable.getTableHeader().setVisible(hasTags);
	}

	@Override
	public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
		this.osmData = newSelection;

		if (!isVisible())
			return;
		geomTree.setModel(createTree());
		updateTable();
		//		signButton.setEnabled(newSelection.size() == 1);
	}

}