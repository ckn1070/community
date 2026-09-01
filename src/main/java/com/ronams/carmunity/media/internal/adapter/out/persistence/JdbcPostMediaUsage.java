package com.ronams.carmunity.media.internal.adapter.out.persistence;

import com.ronams.carmunity.media.api.PostMediaUsage;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class JdbcPostMediaUsage implements PostMediaUsage {

  private final JdbcClient jdbc;

  @Override
  public void replace(UUID actorId, UUID postId, Set<UUID> mediaIds) {
    jdbc.sql(
            """
            UPDATE media_usage SET status = 'RELEASED', released_at = NOW(), updated_at = NOW(), version = version + 1
            WHERE consumer_type = 'POST_BODY' AND consumer_public_id = :post AND status <> 'RELEASED'
              AND asset_id NOT IN (SELECT id FROM media_asset WHERE public_id IN (:assets))
            """)
        .param("post", postId)
        .param("assets", mediaIds.isEmpty() ? Set.of(new UUID(0, 0)) : mediaIds)
        .update();
    for (UUID mediaId : mediaIds) {
      int inserted =
          jdbc.sql(
                  """
                  INSERT INTO media_usage (asset_id, consumer_type, consumer_public_id, status)
                  SELECT id, 'POST_BODY', :post, 'ACTIVE' FROM media_asset
                  WHERE public_id = :asset AND owner_account_public_id = :actor AND status = 'READY'
                  ON CONFLICT (consumer_type, consumer_public_id, asset_id) DO UPDATE
                    SET status = 'ACTIVE', hidden_at = NULL, released_at = NULL, updated_at = NOW(), version = media_usage.version + 1
                  """)
              .param("post", postId)
              .param("asset", mediaId)
              .param("actor", actorId)
              .update();
      if (inserted != 1) throw new IllegalStateException("claim할 수 없는 media asset입니다.");
    }
    queueReleasedAssets();
  }

  @Override
  public void hide(UUID postId) {
    jdbc.sql(
            "UPDATE media_usage SET status='HIDDEN_RETAINED', hidden_at=NOW(), updated_at=NOW(), version=version+1 WHERE consumer_public_id=:post AND status='ACTIVE'")
        .param("post", postId)
        .update();
  }

  @Override
  public void release(UUID postId) {
    jdbc.sql(
            "UPDATE media_usage SET status='RELEASED', released_at=NOW(), updated_at=NOW(), version=version+1 WHERE consumer_public_id=:post AND status<>'RELEASED'")
        .param("post", postId)
        .update();
    queueReleasedAssets();
  }

  private void queueReleasedAssets() {
    jdbc.sql(
            "UPDATE media_asset a SET status='DELETE_PENDING',updated_at=NOW(),version=version+1 WHERE a.status='READY' AND EXISTS(SELECT 1 FROM media_usage released WHERE released.asset_id=a.id AND released.status='RELEASED') AND NOT EXISTS(SELECT 1 FROM media_usage active WHERE active.asset_id=a.id AND active.status<>'RELEASED')")
        .update();
    jdbc.sql(
            "INSERT INTO media_job(public_id,asset_id,type,status) SELECT gen_random_uuid(),a.id,'DELETE_DELIVERY','READY' FROM media_asset a WHERE a.status='DELETE_PENDING' ON CONFLICT DO NOTHING")
        .update();
  }
}
