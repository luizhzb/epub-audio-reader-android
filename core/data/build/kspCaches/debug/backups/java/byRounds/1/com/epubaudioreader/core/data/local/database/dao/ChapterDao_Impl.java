package com.epubaudioreader.core.data.local.database.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.epubaudioreader.core.data.local.database.entity.ChapterEntity;
import java.lang.Class;
import java.lang.Exception;
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
public final class ChapterDao_Impl implements ChapterDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ChapterEntity> __insertionAdapterOfChapterEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteChaptersByBook;

  private final SharedSQLiteStatement __preparedStmtOfUpdateContentFilePath;

  public ChapterDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfChapterEntity = new EntityInsertionAdapter<ChapterEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `chapters` (`id`,`bookId`,`title`,`orderIndex`,`contentFilePath`,`charCount`,`paragraphCount`,`spineIndex`,`href`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ChapterEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getBookId());
        statement.bindString(3, entity.getTitle());
        statement.bindLong(4, entity.getOrderIndex());
        statement.bindString(5, entity.getContentFilePath());
        statement.bindLong(6, entity.getCharCount());
        statement.bindLong(7, entity.getParagraphCount());
        statement.bindLong(8, entity.getSpineIndex());
        statement.bindString(9, entity.getHref());
      }
    };
    this.__preparedStmtOfDeleteChaptersByBook = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM chapters WHERE bookId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfUpdateContentFilePath = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE chapters SET contentFilePath = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertChapters(final List<ChapterEntity> chapters,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfChapterEntity.insertAndReturnIdsList(chapters);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteChaptersByBook(final long bookId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteChaptersByBook.acquire();
        int _argIndex = 1;
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
          __preparedStmtOfDeleteChaptersByBook.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object updateContentFilePath(final long chapterId, final String path,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateContentFilePath.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, path);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, chapterId);
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
          __preparedStmtOfUpdateContentFilePath.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ChapterEntity>> getChaptersByBook(final long bookId) {
    final String _sql = "SELECT * FROM chapters WHERE bookId = ? ORDER BY orderIndex ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bookId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"chapters"}, new Callable<List<ChapterEntity>>() {
      @Override
      @NonNull
      public List<ChapterEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "orderIndex");
          final int _cursorIndexOfContentFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "contentFilePath");
          final int _cursorIndexOfCharCount = CursorUtil.getColumnIndexOrThrow(_cursor, "charCount");
          final int _cursorIndexOfParagraphCount = CursorUtil.getColumnIndexOrThrow(_cursor, "paragraphCount");
          final int _cursorIndexOfSpineIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "spineIndex");
          final int _cursorIndexOfHref = CursorUtil.getColumnIndexOrThrow(_cursor, "href");
          final List<ChapterEntity> _result = new ArrayList<ChapterEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChapterEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBookId;
            _tmpBookId = _cursor.getLong(_cursorIndexOfBookId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpOrderIndex;
            _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
            final String _tmpContentFilePath;
            _tmpContentFilePath = _cursor.getString(_cursorIndexOfContentFilePath);
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            final int _tmpParagraphCount;
            _tmpParagraphCount = _cursor.getInt(_cursorIndexOfParagraphCount);
            final int _tmpSpineIndex;
            _tmpSpineIndex = _cursor.getInt(_cursorIndexOfSpineIndex);
            final String _tmpHref;
            _tmpHref = _cursor.getString(_cursorIndexOfHref);
            _item = new ChapterEntity(_tmpId,_tmpBookId,_tmpTitle,_tmpOrderIndex,_tmpContentFilePath,_tmpCharCount,_tmpParagraphCount,_tmpSpineIndex,_tmpHref);
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
  public Object getChapterById(final long chapterId,
      final Continuation<? super ChapterEntity> $completion) {
    final String _sql = "SELECT * FROM chapters WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, chapterId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ChapterEntity>() {
      @Override
      @Nullable
      public ChapterEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "orderIndex");
          final int _cursorIndexOfContentFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "contentFilePath");
          final int _cursorIndexOfCharCount = CursorUtil.getColumnIndexOrThrow(_cursor, "charCount");
          final int _cursorIndexOfParagraphCount = CursorUtil.getColumnIndexOrThrow(_cursor, "paragraphCount");
          final int _cursorIndexOfSpineIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "spineIndex");
          final int _cursorIndexOfHref = CursorUtil.getColumnIndexOrThrow(_cursor, "href");
          final ChapterEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBookId;
            _tmpBookId = _cursor.getLong(_cursorIndexOfBookId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpOrderIndex;
            _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
            final String _tmpContentFilePath;
            _tmpContentFilePath = _cursor.getString(_cursorIndexOfContentFilePath);
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            final int _tmpParagraphCount;
            _tmpParagraphCount = _cursor.getInt(_cursorIndexOfParagraphCount);
            final int _tmpSpineIndex;
            _tmpSpineIndex = _cursor.getInt(_cursorIndexOfSpineIndex);
            final String _tmpHref;
            _tmpHref = _cursor.getString(_cursorIndexOfHref);
            _result = new ChapterEntity(_tmpId,_tmpBookId,_tmpTitle,_tmpOrderIndex,_tmpContentFilePath,_tmpCharCount,_tmpParagraphCount,_tmpSpineIndex,_tmpHref);
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
  public Object getChapterByOrder(final long bookId, final int orderIndex,
      final Continuation<? super ChapterEntity> $completion) {
    final String _sql = "SELECT * FROM chapters WHERE bookId = ? AND orderIndex = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, bookId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, orderIndex);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ChapterEntity>() {
      @Override
      @Nullable
      public ChapterEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfBookId = CursorUtil.getColumnIndexOrThrow(_cursor, "bookId");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfOrderIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "orderIndex");
          final int _cursorIndexOfContentFilePath = CursorUtil.getColumnIndexOrThrow(_cursor, "contentFilePath");
          final int _cursorIndexOfCharCount = CursorUtil.getColumnIndexOrThrow(_cursor, "charCount");
          final int _cursorIndexOfParagraphCount = CursorUtil.getColumnIndexOrThrow(_cursor, "paragraphCount");
          final int _cursorIndexOfSpineIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "spineIndex");
          final int _cursorIndexOfHref = CursorUtil.getColumnIndexOrThrow(_cursor, "href");
          final ChapterEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpBookId;
            _tmpBookId = _cursor.getLong(_cursorIndexOfBookId);
            final String _tmpTitle;
            _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            final int _tmpOrderIndex;
            _tmpOrderIndex = _cursor.getInt(_cursorIndexOfOrderIndex);
            final String _tmpContentFilePath;
            _tmpContentFilePath = _cursor.getString(_cursorIndexOfContentFilePath);
            final int _tmpCharCount;
            _tmpCharCount = _cursor.getInt(_cursorIndexOfCharCount);
            final int _tmpParagraphCount;
            _tmpParagraphCount = _cursor.getInt(_cursorIndexOfParagraphCount);
            final int _tmpSpineIndex;
            _tmpSpineIndex = _cursor.getInt(_cursorIndexOfSpineIndex);
            final String _tmpHref;
            _tmpHref = _cursor.getString(_cursorIndexOfHref);
            _result = new ChapterEntity(_tmpId,_tmpBookId,_tmpTitle,_tmpOrderIndex,_tmpContentFilePath,_tmpCharCount,_tmpParagraphCount,_tmpSpineIndex,_tmpHref);
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
  public Object getContentFilePath(final long chapterId,
      final Continuation<? super String> $completion) {
    final String _sql = "SELECT contentFilePath FROM chapters WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, chapterId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<String>() {
      @Override
      @Nullable
      public String call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final String _result;
          if (_cursor.moveToFirst()) {
            if (_cursor.isNull(0)) {
              _result = null;
            } else {
              _result = _cursor.getString(0);
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
