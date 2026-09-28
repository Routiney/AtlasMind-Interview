package com.plexus.personal.document.infrastructure;
import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface DocumentChunkMapper {
 @Insert("insert into document_chunks(document_id,user_id,chunk_index,content,embedding) values(#{documentId},#{userId},#{chunkIndex},#{content},#{embedding})") int insert(DocumentChunkRow row);
 @Delete("delete from document_chunks where document_id=#{documentId}") int deleteByDocument(Long documentId);
 @Select("select c.id,c.document_id,c.user_id,c.chunk_index,c.content,c.embedding,d.original_filename as source_filename from document_chunks c join documents d on d.id=c.document_id where c.user_id=#{userId} order by c.created_at desc") @Results(value={@Result(property="id",column="id"),@Result(property="documentId",column="document_id"),@Result(property="userId",column="user_id"),@Result(property="chunkIndex",column="chunk_index"),@Result(property="content",column="content"),@Result(property="embedding",column="embedding"),@Result(property="sourceFilename",column="source_filename")}) List<DocumentChunkRow> findByUserId(Long userId);
}
