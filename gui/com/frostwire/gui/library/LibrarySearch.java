package com.frostwire.gui.library;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.limewire.util.StringUtils;

import com.frostwire.alexandria.InternetRadioStation;
import com.frostwire.alexandria.Playlist;
import com.frostwire.alexandria.PlaylistItem;
import com.frostwire.gui.bittorrent.TorrentUtil;
import com.frostwire.gui.components.IconSearchField;
import com.limegroup.gnutella.MediaType;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchInformation;
import com.limegroup.gnutella.gui.search.SearchMediator;
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import com.limegroup.gnutella.settings.LibrarySettings;
import com.limegroup.gnutella.settings.SharingSettings;

public class LibrarySearch extends JPanel {

    private static final long serialVersionUID = 2266243762191789491L;

    private JLabel statusLabel;
    private IconSearchField searchField;

    private SearchRunnable currentSearchRunnable;

    private int resultsCount;
    private String status;

    public LibrarySearch() {
        setupUI();
    }
    
    public void searchFor(final String query) {
		GUIMediator.safeInvokeLater(new Runnable() {

			@Override
			public void run() {
		        GUIMediator.instance().setWindow(GUIMediator.Tabs.LIBRARY);
		        LibraryMediator.instance().getLibraryFiles().selectFinishedDownloads();
		        
				if (searchField != null) {
					SearchLibraryAction searchAction = new SearchLibraryAction();

					searchField.setText(".");
					searchAction.actionPerformed(null);
					searchField.setText("");

					if (query.length() < 50) {
						searchField.setText(query);
					} else {
						searchField.setText(query.substring(0,49));
					}
					
					searchAction.actionPerformed(null);
				}
			}
		});
    }

    public void addResults(int n) {
        if (n < 0) {
            return;
        }

        resultsCount += n;
        setStatus(resultsCount + " " + I18n.tr("search results"));
    }

    public void clear() {
        setStatus("");
        searchField.setText("");
        resultsCount = 0;
    }

    public void pushStatus(final String newStatus) {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                statusLabel.setText(newStatus);
            }
        });
    }

    public void revertStatus() {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                statusLabel.setText(status);
            }
        });
    }

    void setStatus(String status) {
        this.status = status;
        statusLabel.setText(status);
    }

    protected void setupUI() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(2, 2, 4, 2));

        GridBagConstraints c;

        statusLabel = new JLabel();
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        add(statusLabel, c);

        searchField = new IconSearchField(10, GUIMediator.getThemeImage("search_tab"));
        c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_END;
        c.gridwidth = GridBagConstraints.REMAINDER;
        add(searchField, c);

        searchField.addKeyListener(new KeyAdapter() {
            private Action a = new SearchLibraryAction();

            private long lastSearch;

            @Override
            public void keyReleased(KeyEvent e) {
                if (searchField.getText().length() == 0 && (e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE)) {
                    lastSearch = 0;
                    searchField.setText(".");
                    a.actionPerformed(null);
                    searchField.setText("");
                }

                if (System.currentTimeMillis() - lastSearch > 75) {
                    a.actionPerformed(null);
                    lastSearch = System.currentTimeMillis();
                }
            }
        });
        
        searchField.addFocusListener(new FocusListener() {
			
			@Override
			public void focusLost(FocusEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void focusGained(FocusEvent e) {
				//if there's nothing selected for search, select Audio directory holder.
				if (LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder() == null
					&& LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist() == null) {
					LibraryMediator.instance().getLibraryFiles().selectAudio();
				}					
			}
		});
    }
    
    

    private class SearchLibraryAction extends AbstractAction {

        private static final long serialVersionUID = -2182314529781104010L;

        public SearchLibraryAction() {
            putValue(Action.NAME, I18n.tr("Search"));
        }

        public boolean validate(SearchInformation info) {
            switch (SearchMediator.validateInfo(info)) {
            case SearchMediator.QUERY_EMPTY:
                return false;
            case SearchMediator.QUERY_XML_TOO_LONG:
                // cannot happen
            case SearchMediator.QUERY_VALID:
            default:
                return true;
            }
        }

        public void actionPerformed(ActionEvent e) {
            String query = searchField.getText().trim();
            if (query.length() == 0) {
                searchField.getToolkit().beep();
                return;
            }
            final SearchInformation info = SearchInformation.createKeywordSearch(query, null, MediaType.getAnyTypeMediaType());
            if (!validate(info)) {
                return;
            }
            searchField.addToDictionary();

            if (currentSearchRunnable != null) {
                currentSearchRunnable.cancel();
            }
            
            DirectoryHolder directoryHolder = LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder();
            
            if (directoryHolder instanceof InternetRadioDirectoryHolder) {
                currentSearchRunnable = new SearchInternetRadioStationsRunnable(query);
                BackgroundExecutorService.schedule(currentSearchRunnable);
            } else if (directoryHolder != null) {
                currentSearchRunnable = new SearchFilesRunnable(query);
                BackgroundExecutorService.schedule(currentSearchRunnable);
            }

            Playlist playlist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
            if (playlist != null) {
                currentSearchRunnable = new SearchPlaylistItemsRunnable(query);
                BackgroundExecutorService.schedule(currentSearchRunnable);
            }
        }
    }

    private abstract class SearchRunnable implements Runnable {
        protected boolean canceled;

        public void cancel() {
            canceled = true;
        }
    }

    private final class SearchFilesRunnable extends SearchRunnable {

        private final String _query;
        private final DirectoryHolder directoryHolder;

        public SearchFilesRunnable(String query) {
            _query = query;
            directoryHolder = LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder();
            canceled = false;

            // weird case
            if (directoryHolder == null) {
                canceled = true;
            }
        }

        public void run() {
            if (canceled) {
                return;
            }
            // special case for Finished Downloads
            if (_query.equals(".") && directoryHolder instanceof SavedFilesDirectoryHolder) {
                GUIMediator.safeInvokeLater(new Runnable() {
                    public void run() {
                        LibraryMediator.instance().updateTableFiles(directoryHolder);
                        setStatus("");
                        resultsCount = 0;
                    }
                });
                return;
            }

            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    LibraryFilesTableMediator.instance().clearTable();
                    statusLabel.setText("");
                    resultsCount = 0;
                }
            });
            
            if (directoryHolder instanceof MediaTypeSavedFilesDirectoryHolder) {
                List<File> cache = new ArrayList<File>(((MediaTypeSavedFilesDirectoryHolder) directoryHolder).getCache());
                if (cache.size() > 0) {
                    search(cache);
                    return;
                }
            }

            File torrentDataDirFile = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
            if (directoryHolder instanceof TorrentDirectoryHolder) {
                torrentDataDirFile = ((TorrentDirectoryHolder) directoryHolder).getDirectory();
            }

            Set<File> ignore = TorrentUtil.getIgnorableFiles();

            Set<File> directories = new HashSet<File>(LibrarySettings.DIRECTORIES_TO_INCLUDE.getValue());
            directories.removeAll(LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
            for (File dir : directories) {
                if (dir == null) {
                    continue;
                }
                if (dir.equals(torrentDataDirFile)) {
                    search(dir, ignore, LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                } else if (dir.equals(LibrarySettings.USER_MUSIC_FOLDER) &&
                        directoryHolder instanceof MediaTypeSavedFilesDirectoryHolder &&
                        !((MediaTypeSavedFilesDirectoryHolder) directoryHolder).getMediaType().equals(MediaType.getAudioMediaType())) {
                    continue;
                } else {
                    search(dir, new HashSet<File>(), LibrarySettings.DIRECTORIES_NOT_TO_INCLUDE.getValue());
                }
            }
        }

        /**
         * It searches _query in haystackDir.
         * 
         * @param haystackDir
         * @param excludeFiles - Usually a list of incomplete files.
         */
        private void search(File haystackDir, Set<File> excludeFiles, Set<File> exludedSubFolders) {
            if (canceled) {
                return;
            }

            if (haystackDir == null || !haystackDir.isDirectory() || !haystackDir.exists()) {
                return;
            }

            final List<File> directories = new ArrayList<File>();
            final List<File> results = new ArrayList<File>();
            SearchFileFilter searchFilter = new SearchFileFilter(_query);

            for (File child : haystackDir.listFiles(searchFilter)) {
                if (canceled) {
                    return;
                }

                /////
                //Stop search if the user selected another item in the library tree
                DirectoryHolder currentDirectoryHolder = LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder();
                if (!directoryHolder.equals(currentDirectoryHolder)) {
                    return;
                }
                /////

                if (excludeFiles.contains(child)) {
                    continue;
                }
                
                if (child.isHidden()) {
                    continue;
                }

                if (child.isDirectory() && !exludedSubFolders.contains(child)) {
                    directories.add(child);
                } else if (child.isFile()) {
                    if (directoryHolder instanceof SavedFilesDirectoryHolder) {
                        if (searchFilter.accept(child, false)) {
                            results.add(child);
                        }
                    } else if (directoryHolder.accept(child)) {
                        results.add(child);
                    }
                }
            }

            Runnable r = new Runnable() {
                public void run() {
                    LibraryMediator.instance().addFilesToLibraryTable(results);
                }
            };
            GUIMediator.safeInvokeLater(r);

            for (File directory : directories) {
                search(directory, excludeFiles, exludedSubFolders);
            }
        }
        
        private void search(List<File> cache) {
            if (canceled) {
                return;
            }
            
            final List<File> results = new ArrayList<File>();
            SearchFileFilter searchFilter = new SearchFileFilter(_query);
            
            for (File file : cache) {
                if (canceled) {
                    return;
                }

                /////
                //Stop search if the user selected another item in the library tree
                DirectoryHolder currentDirectoryHolder = LibraryMediator.instance().getLibraryFiles().getSelectedDirectoryHolder();
                if (!directoryHolder.equals(currentDirectoryHolder)) {
                    return;
                }
                /////

                if (file.isHidden()) {
                    continue;
                }

                if (searchFilter.accept(file)) {
                    results.add(file);
                }
            }

            Runnable r = new Runnable() {
                public void run() {
                    LibraryMediator.instance().addFilesToLibraryTable(results);
                }
            };
            GUIMediator.safeInvokeLater(r);
        }
    }

    private static final class SearchFileFilter implements FileFilter {

        private final String[] _tokens;

        public SearchFileFilter(String query) {
            _tokens = query.toLowerCase(Locale.US).split(" ");
        }

        public boolean accept(File pathname) {
            return accept(pathname, true);
        }

        /**
         * 
         * @param pathname
         * @param includeAllDirectories - if true, it will say TRUE to any directory
         * @return
         */        
        public boolean accept(File pathname, boolean includeAllDirectories) {
            if (pathname.isDirectory() && includeAllDirectories) {
                return true;
            }

            String name = pathname.getAbsolutePath();

            for (String token : _tokens) {
                if (!name.toLowerCase(Locale.US).contains(token)) {
                    return false;
                }
            }

            return true;
        }
    }

    private final class SearchPlaylistItemsRunnable extends SearchRunnable {

        private final String query;
        private final Playlist playlist;

        public SearchPlaylistItemsRunnable(String query) {
            this.query = query;
            playlist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
            canceled = false;

            // weird case
            if (playlist == null) {
                canceled = true;
            }
        }

        public void run() {
            if (canceled) {
                return;
            }

            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    LibraryPlaylistsTableMediator.instance().clearTable();
                    setStatus("");
                    statusLabel.setText("");
                    resultsCount = 0;
                }
            });

            search();
        }

        private void search() {
            if (canceled) {
                return;
            }
            
            String sql = null;
            List<List<Object>> rows = null;

            //Show everything
            if (StringUtils.isNullOrEmpty(query,true) || query.equals(".")) {
                LibraryMediator.instance().getLibraryPlaylists().selectPlaylist(playlist);
                return;
            } else {
                //Full text search
                sql = "SELECT T.playlistItemId, T.filePath, T.fileName, T.fileSize, T.fileExtension, T.trackTitle, T.trackDurationInSecs, T.trackArtist, T.trackAlbum, T.coverArtPath, T.trackBitrate, T.trackComment, T.trackGenre, T.trackNumber, T.trackYear, T.starred FROM FT_SEARCH_DATA(?, 0, 0) FT, PLAYLISTITEMS T WHERE FT.TABLE='PLAYLISTITEMS' AND T.playlistItemId = FT.KEYS[0]";
                rows = LibraryMediator.getLibrary().getDB().getDatabase().query(sql, query);
            }

            final List<PlaylistItem> results = new ArrayList<PlaylistItem>();

            for (List<Object> row : rows) {
                if (canceled) {
                    return;
                }

                /////
                //Stop search if the user selected another item in the playlist list
                Playlist currentPlaylist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
                if (!playlist.equals(currentPlaylist)) {
                    return;
                }
                /////

                PlaylistItem item = new PlaylistItem(null);
                item.getDB().fill(row, item);
                results.add(item);

                if (results.size() > 100) {
                    Runnable r = new Runnable() {
                        public void run() {
                            LibraryMediator.instance().addItemsToLibraryTable(results);
                            results.clear();
                        }
                    };
                    GUIMediator.safeInvokeLater(r);
                }
            }

            Runnable r = new Runnable() {
                public void run() {
                    LibraryMediator.instance().addItemsToLibraryTable(results);
                }
            };
            GUIMediator.safeInvokeLater(r);
        }
    }
    
    private final class SearchInternetRadioStationsRunnable extends SearchRunnable {

        private final String query;

        public SearchInternetRadioStationsRunnable(String query) {
            this.query = query;
            canceled = false;
        }

        public void run() {
            if (canceled) {
                return;
            }

            GUIMediator.safeInvokeLater(new Runnable() {
                public void run() {
                    LibraryInternetRadioTableMediator.instance().clearTable();
                    setStatus("");
                    statusLabel.setText("");
                    resultsCount = 0;
                }
            });

            search();
        }

        private void search() {
            if (canceled) {
                return;
            }
            
            String sql = null;
            List<List<Object>> rows = null;

            //Show everything
            if (StringUtils.isNullOrEmpty(query,true) || query.equals(".")) {
            	LibraryMediator.instance().getLibraryFiles().selectRadio();
            	//sql="SELECT T.internetRadioStationId, T.name, T.description, T.url, T.bitrate, T.type, T.website, T.genre, T.pls FROM INTERNETRADIOSTATIONS T";
            	//rows = LibraryMediator.getLibrary().getDB().getDatabase().query(sql);
            	return;
            } else {
            	//Full text search
            	sql = "SELECT T.internetRadioStationId, T.name, T.description, T.url, T.bitrate, T.type, T.website, T.genre, T.pls, T.bookmarked FROM FT_SEARCH_DATA(?, 0, 0) FT, INTERNETRADIOSTATIONS T WHERE FT.TABLE='INTERNETRADIOSTATIONS' AND T.internetRadioStationId = FT.KEYS[0]";
            	rows = LibraryMediator.getLibrary().getDB().getDatabase().query(sql, query);
            }

            final List<InternetRadioStation> results = new ArrayList<InternetRadioStation>();

            for (List<Object> row : rows) {
                if (canceled) {
                    return;
                }

                /////
                //Stop search if the user selected another item in the playlist list
//                Playlist currentPlaylist = LibraryMediator.instance().getLibraryPlaylists().getSelectedPlaylist();
//                if (!playlist.equals(currentPlaylist)) {
//                    return;
//                }
                /////

                InternetRadioStation item = new InternetRadioStation(LibraryMediator.getLibrary());
                item.getDB().fill(row, item);
                results.add(item);

                if (results.size() > 100) {
                    Runnable r = new Runnable() {
                        public void run() {
                            LibraryMediator.instance().addInternetRadioStationsToLibraryTable(results);
                            results.clear(); // TODO: Fix this error, check for thread issues
                        }
                    };
                    GUIMediator.safeInvokeLater(r);
                }
            }

            Runnable r = new Runnable() {
                public void run() {
                    LibraryMediator.instance().addInternetRadioStationsToLibraryTable(results);
                }
            };
            GUIMediator.safeInvokeLater(r);
        }
    }
}
