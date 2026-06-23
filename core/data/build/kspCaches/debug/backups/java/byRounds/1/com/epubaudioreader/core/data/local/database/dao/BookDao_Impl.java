package com.epubaudioreader.core.data.local.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.epubaudioreader.core.data.local.database.entity.BookEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class BookDao_Impl implements BookDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BookEntity> __insertionAdapterOfBookEntity;

  private final EntityDeletionOrUpdateAdapter<BookEntity> __deletionAdapterOfBookEntity;

  private final EntityDeletionOrUpdateAdapter<BookEntity> __updateAdapterOfBookEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateLastRead;

  public BookDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBookEntity = new EntityInsertionAdapter<BookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `books` (`id`,`title`,`authors`,`language`,`identifier`,`description`,`coverImagePath`,`filePath`,`importDate`,`lastReadDate`,`totalChapters`,`totalChars`,`fileSize`,`hash`,`lastReadChapterId`,`lastReadPosition`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BookEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getAuthors());
        statement.bindString(4, entity.getLanguage());
        statement.bindString(5, entity.getIdentifier());
        if (entity.getDescription() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getDescription());
        }
        if (entity.getCoverImagePath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCoverImagePath());
        }
        statement.bindString(8, entity.getFilePath());
        statement.bindLong(9, entity.getImportDate());
        if (entity.getLastReadDate() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getLastReadDate());
        }
        statement.bindLong(11, entity.getTotalChapters());
        statement.bindLong(12, entity.getTotalChars());
        statement.bindLong(13, entity.getFileSize());
        statement.bindString(14, entity.getHash());
        if (entity.getLastReadChapterId() == null) {
          statement.bindNull(15);
        } else {
          statement.bindLong(15, entity.getLastReadChapterId());
        }
        if (entity.getLastReadPosition() == null) {
          statement.bindNull(16);
        } else {
          statement.bindLong(16, entity.getLastReadPosition());
        }
      }
    };
    this.__deletionAdapterOfBookEntity = new EntityDeletionOrUpdateAdapter<BookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `books` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BookEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfBookEntity = new EntityDeletionOrUpdateAdapter<BookEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `books` SET `id` = ?,`title` = ?,`authors` = ?,`language` = ?,`identifier` = ?,`description` = ?,`coverImagePath` = ?,`filePath` = ?,`importDate` = ?,`lastReadDate` = ?,`totalChapters` = ?,`totalChars` = ?,`fileSize` = ?,`hash` = ?,`lastReadChapterId` = ?,`lastReadPosition` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BookEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTitle());
        statement.bindString(3, entity.getAuthors());
        statement.bindString(4, entity.getLanguage());
        statement.bindString(5, entity.getIdentifier());
        if (entity.getDescription() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getDescription());
        }
        if (entity.getCoverImagePath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCoverImagePath());
        }
        statement.bindString(8, entity.getFilePath());
        statement.bindLong(9, entity.getImportDate());
        if (entity.getLastReadDate() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getLastReadDate());
        }
        statement.bindLong(11, entity.getTotalChapters());
        statement.bindLong(12, entity.getTotalChars());
        statement.bindLong(13, entity.getFileSize());
        statement.bindString(14, entity.getHash());
        if (entity.getLastReadChapterId() == null) {
          statement.bindNull(15);
        } else {
          statement.bindLong(15, entity.getLastReadChapterId());
        }
        if (entity.getLastReadPosition() == null) {
          statement.bindNull(16);
        } else {
          statement.bindLong(16, entity.getLastReadPosition());
        }
        statement.bindLong(17, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateLastRead = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE books SET lastReadDate = ?, lastReadChapterId = ?, lastReadPosition = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertBook(final BookEntity book, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfBookEntity.insertAndReturnId(book);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteBook(final BookEntity book, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfBookEntity.handle(book);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateBook(final BookEntity book, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfBookEntity.handle(book);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateLastRead(final long bookId, final Long chapterId, final Integer position,
      final long timestamp, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateLastRead.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, timestamp);
        _argIndex = 2;
        if (chapterId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, chapterId);
        }
        _argIndex = 3;
        if (position == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindLong(_argIndex, position);
        }
        _argIndex = 4;
        _stmt.bindLong(_argIndex, bookId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfUpdateLastRead.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BookEntity>> getAllBooks() {
    final String _sql = "SELECT * FROM books ORDER BY COALESCE(lastReadDate, importDate) DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"books"}, new Callable<List<BookEntity>>() {
      @Override
      @NonNull
      public List<BookEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthors = CursorUtil.getColumnIndexOrThrow(_cursor, "authors");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfIdentifier = CursorUtil.getColumnIndexOrThrow(_cursor, "identifier");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfCoverImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverImagePath");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfImportDate = CursorUtil.getColumnIndexOrThrow(_cursor, "importDate");
          final int _cursorIndexOfLastReadDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadDate");
          final int _cursorIndexOfTotalChapters = CursorUtil.getColumnIndexOrThrow(_cursor, "totalChapters");
          final int _cursorIndexOfTotalChars = CursorUtil.getColumnIndexOrThrow(_cursor, "totalChars");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfLastReadChapterId = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadChapterId");
          final int _cursorIndexOfLastReadPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadPosition");
          final List<BookEntity> _result = new ArrayList<BookEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BookEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthors;
            _tmpAuthors = _cursor.getString(_cursorIndexOfAuthors);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpIdentifier;
            _tmpIdentifier = _cursor.getString(_cursorIndexOfIdentifier);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final String _tmpCoverImagePath;
            if (_cursor.isNull(_cursorIndexOfCoverImagePath)) {
              _tmpCoverImagePath = null;
            } else {
              _tmpCoverImagePath = _cursor.getString(_cursorIndexOfCoverImagePath);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final long _tmpImportDate;
            _tmpImportDate = _cursor.getLong(_cursorIndexOfImportDate);
            final Long _tmpLastReadDate;
            if (_cursor.isNull(_cursorIndexOfLastReadDate)) {
              _tmpLastReadDate = null;
            } else {
              _tmpLastReadDate = _cursor.getLong(_cursorIndexOfLastReadDate);
            }
            final int _tmpTotalChapters;
            _tmpTotalChapters = _cursor.getInt(_cursorIndexOfTotalChapters);
            final long _tmpTotalChars;
            _tmpTotalChars = _cursor.getLong(_cursorIndexOfTotalChars);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final Long _tmpLastReadChapterId;
            if (_cursor.isNull(_cursorIndexOfLastReadChapterId)) {
              _tmpLastReadChapterId = null;
            } else {
              _tmpLastReadChapterId = _cursor.getLong(_cursorIndexOfLastReadChapterId);
            }
            final Integer _tmpLastReadPosition;
            if (_cursor.isNull(_cursorIndexOfLastReadPosition)) {
              _tmpLastReadPosition = null;
            } else {
              _tmpLastReadPosition = _cursor.getInt(_cursorIndexOfLastReadPosition);
            }
            _item = new BookEntity(_tmpId,_tmpTitle,_tmpAuthors,_tmpLanguage,_tmpIdentifier,_tmpDescription,_tmpCoverImagePath,_tmpFilePath,_tmpImportDate,_tmpLastReadDate,_tmpTotalChapters,_tmpTotalChars,_tmpFileSize,_tmpHash,_tmpLastReadChapterId,_tmpLastReadPosition);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<BookEntity> getBookById(final long bookId) {
    final String _sql = "SELECT * FROM books WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bookId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"books"}, new Callable<BookEntity>() {
      @Override
      @Nullable
      public BookEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthors = CursorUtil.getColumnIndexOrThrow(_cursor, "authors");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfIdentifier = CursorUtil.getColumnIndexOrThrow(_cursor, "identifier");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfCoverImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverImagePath");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfImportDate = CursorUtil.getColumnIndexOrThrow(_cursor, "importDate");
          final int _cursorIndexOfLastReadDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadDate");
          final int _cursorIndexOfTotalChapters = CursorUtil.getColumnIndexOrThrow(_cursor, "totalChapters");
          final int _cursorIndexOfTotalChars = CursorUtil.getColumnIndexOrThrow(_cursor, "totalChars");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfLastReadChapterId = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadChapterId");
          final int _cursorIndexOfLastReadPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadPosition");
          final BookEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthors;
            _tmpAuthors = _cursor.getString(_cursorIndexOfAuthors);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpIdentifier;
            _tmpIdentifier = _cursor.getString(_cursorIndexOfIdentifier);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final String _tmpCoverImagePath;
            if (_cursor.isNull(_cursorIndexOfCoverImagePath)) {
              _tmpCoverImagePath = null;
            } else {
              _tmpCoverImagePath = _cursor.getString(_cursorIndexOfCoverImagePath);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final long _tmpImportDate;
            _tmpImportDate = _cursor.getLong(_cursorIndexOfImportDate);
            final Long _tmpLastReadDate;
            if (_cursor.isNull(_cursorIndexOfLastReadDate)) {
              _tmpLastReadDate = null;
            } else {
              _tmpLastReadDate = _cursor.getLong(_cursorIndexOfLastReadDate);
            }
            final int _tmpTotalChapters;
            _tmpTotalChapters = _cursor.getInt(_cursorIndexOfTotalChapters);
            final long _tmpTotalChars;
            _tmpTotalChars = _cursor.getLong(_cursorIndexOfTotalChars);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final Long _tmpLastReadChapterId;
            if (_cursor.isNull(_cursorIndexOfLastReadChapterId)) {
              _tmpLastReadChapterId = null;
            } else {
              _tmpLastReadChapterId = _cursor.getLong(_cursorIndexOfLastReadChapterId);
            }
            final Integer _tmpLastReadPosition;
            if (_cursor.isNull(_cursorIndexOfLastReadPosition)) {
              _tmpLastReadPosition = null;
            } else {
              _tmpLastReadPosition = _cursor.getInt(_cursorIndexOfLastReadPosition);
            }
            _result = new BookEntity(_tmpId,_tmpTitle,_tmpAuthors,_tmpLanguage,_tmpIdentifier,_tmpDescription,_tmpCoverImagePath,_tmpFilePath,_tmpImportDate,_tmpLastReadDate,_tmpTotalChapters,_tmpTotalChars,_tmpFileSize,_tmpHash,_tmpLastReadChapterId,_tmpLastReadPosition);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getBookEntityById(final long bookId,
      final Continuation<? super BookEntity> $completion) {
    final String _sql = "SELECT * FROM books WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bookId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BookEntity>() {
      @Override
      @Nullable
      public BookEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfAuthors = CursorUtil.getColumnIndexOrThrow(_cursor, "authors");
          final int _cursorIndexOfLanguage = CursorUtil.getColumnIndexOrThrow(_cursor, "language");
          final int _cursorIndexOfIdentifier = CursorUtil.getColumnIndexOrThrow(_cursor, "identifier");
          final int _cursorIndexOfDescription = CursorUtil.getColumnIndexOrThrow(_cursor, "description");
          final int _cursorIndexOfCoverImagePath = CursorUtil.getColumnIndexOrThrow(_cursor, "coverImagePath");
          final int _cursorIndexOfFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "filePath");
          final int _cursorIndexOfImportDate = CursorUtil.getColumnIndexOrThrow(_cursor, "importDate");
          final int _cursorIndexOfLastReadDate = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadDate");
          final int _cursorIndexOfTotalChapters = CursorUtil.getColumnIndexOrThrow(_cursor, "totalChapters");
          final int _cursorIndexOfTotalChars = CursorUtil.getColumnIndexOrThrow(_cursor, "totalChars");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfLastReadChapterId = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadChapterId");
          final int _cursorIndexOfLastReadPosition = CursorUtil.getColumnIndexOrThrow(_cursor, "lastReadPosition");
          final BookEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final String _tmpAuthors;
            _tmpAuthors = _cursor.getString(_cursorIndexOfAuthors);
            final String _tmpLanguage;
            _tmpLanguage = _cursor.getString(_cursorIndexOfLanguage);
            final String _tmpIdentifier;
            _tmpIdentifier = _cursor.getString(_cursorIndexOfIdentifier);
            final String _tmpDescription;
            if (_cursor.isNull(_cursorIndexOfDescription)) {
              _tmpDescription = null;
            } else {
              _tmpDescription = _cursor.getString(_cursorIndexOfDescription);
            }
            final String _tmpCoverImagePath;
            if (_cursor.isNull(_cursorIndexOfCoverImagePath)) {
              _tmpCoverImagePath = null;
            } else {
              _tmpCoverImagePath = _cursor.getString(_cursorIndexOfCoverImagePath);
            }
            final String _tmpFilePath;
            _tmpFilePath = _cursor.getString(_cursorIndexOfFilePath);
            final long _tmpImportDate;
            _tmpImportDate = _cursor.getLong(_cursorIndexOfImportDate);
            final Long _tmpLastReadDate;
            if (_cursor.isNull(_cursorIndexOfLastReadDate)) {
              _tmpLastReadDate = null;
            } else {
              _tmpLastReadDate = _cursor.getLong(_cursorIndexOfLastReadDate);
            }
            final int _tmpTotalChapters;
            _tmpTotalChapters = _cursor.getInt(_cursorIndexOfTotalChapters);
            final long _tmpTotalChars;
            _tmpTotalChars = _cursor.getLong(_cursorIndexOfTotalChars);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final Long _tmpLastReadChapterId;
            if (_cursor.isNull(_cursorIndexOfLastReadChapterId)) {
              _tmpLastReadChapterId = null;
            } else {
              _tmpLastReadChapterId = _cursor.getLong(_cursorIndexOfLastReadChapterId);
            }
            final Integer _tmpLastReadPosition;
            if (_cursor.isNull(_cursorIndexOfLastReadPosition)) {
              _tmpLastReadPosition = null;
            } else {
              _tmpLastReadPosition = _cursor.getInt(_cursorIndexOfLastReadPosition);
            }
            _result = new BookEntity(_tmpId,_tmpTitle,_tmpAuthors,_tmpLanguage,_tmpIdentifier,_tmpDescription,_tmpCoverImagePath,_tmpFilePath,_tmpImportDate,_tmpLastReadDate,_tmpTotalChapters,_tmpTotalChars,_tmpFileSize,_tmpHash,_tmpLastReadChapterId,_tmpLastReadPosition);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findBookByFilePath(final String filePath,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT id FROM books WHERE filePath = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, filePath);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getLong(0);
            }
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findBookByHash(final String hash, final Continuation<? super Long> $completion) {
    final String _sql = "SELECT id FROM books WHERE hash = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, hash);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getLong(0);
            }
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
