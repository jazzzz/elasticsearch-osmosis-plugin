package org.openstreetmap.osmosis.plugin.elasticsearch;

import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.Bound;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.plugin.elasticsearch.dao.EntityDao;
import org.openstreetmap.osmosis.plugin.elasticsearch.index.IndexBuilder;
import org.openstreetmap.osmosis.plugin.elasticsearch.index.SpecialiazedIndex;
import org.openstreetmap.osmosis.plugin.elasticsearch.service.IndexService;

public class ElasticSearchWriterTask implements Sink {

	private static final Logger LOG = Logger.getLogger(ElasticSearchWriterTask.class.getName());

    private int boundProcessedCounter = 0;
	private int nodeProcessedCounter = 0;
	private int wayProcessedCounter = 0;
	private int relationProcessedCounter = 0;

	private final IndexService indexService;
	private final EntityDao entityDao;
	private final Set<SpecialiazedIndex> specIndexes;

	public ElasticSearchWriterTask(IndexService indexService, EntityDao entityDao, Set<SpecialiazedIndex> specIndexes) {
		this.indexService = indexService;
		this.entityDao = entityDao;
		this.specIndexes = specIndexes;
	}

    @Override
    public void initialize( final Map<String, Object> stringObjectMap )
    {
    }

	@Override
	public void process(EntityContainer entityContainer) {
		Entity entity = entityContainer.getEntity();
		switch (entity.getType()) {
		case Node:
			entityDao.save((Node) entity);
			nodeProcessedCounter++;
			break;
		case Way:
			entityDao.save((Way) entity);
			wayProcessedCounter++;
			break;
		case Relation:
			entityDao.save((Relation) entity);
			relationProcessedCounter++;
			break;
		case Bound:
			entityDao.save((Bound) entity);
			boundProcessedCounter++;
			break;
		}
	}

	@Override
	public void complete() {
		LOG.info("OSM index creation completed!\n" +
				"total processed bounds: ...... " + boundProcessedCounter + "\n" +
				"total processed nodes: ....... " + nodeProcessedCounter + "\n" +
				"total processed ways: ........ " + wayProcessedCounter + "\n" +
				"total processed relations: ... " + relationProcessedCounter);
		buildSpecializedIndex();
	}

	private void buildSpecializedIndex() {
		for (SpecialiazedIndex index : specIndexes) {
			try {
				IndexBuilder indexBuilder = index.getIndexBuilderClass().newInstance();
				LOG.info("Creating specialized index " + index.name());
				String indexName = entityDao.getIndexName() + "-" + indexBuilder.getIndexName();
				indexService.createIndex(indexName, indexBuilder.getIndexMapping());
				LOG.info("Building specialized index " + index.name());
				indexBuilder.buildIndex(indexService);
			} catch (Exception e) {
				LOG.log(Level.SEVERE, "Unable to build index", e);
			}
		}
	}

	@Override
	public void release() {
		float consumedMemoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
				/ (float) Math.pow(1024, 2);
		LOG.info(String.format("Estimated memory consumption: %.2f MB ", consumedMemoryMb));
		indexService.refresh();
		indexService.getClient().close();
	}

}
