/*
 * Copyright (c) 2002-2014, Mairie de Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.document.modules.solr.indexer;
import fr.paris.lutece.plugins.document.business.Document;
import fr.paris.lutece.plugins.document.business.DocumentHome;
import fr.paris.lutece.plugins.document.business.DocumentType;
import fr.paris.lutece.plugins.document.business.DocumentTypeHome;
import fr.paris.lutece.plugins.document.business.attributes.DocumentAttribute;
import fr.paris.lutece.plugins.document.business.attributes.DocumentAttributeHome;
import fr.paris.lutece.plugins.document.business.category.Category;
import fr.paris.lutece.plugins.document.business.portlet.DocumentListPortletHome;
import fr.paris.lutece.plugins.document.service.publishing.PublishingService;
import fr.paris.lutece.plugins.document.utils.DocumentIndexerUtils;
import fr.paris.lutece.plugins.lucene.service.indexer.IFileIndexer;
import fr.paris.lutece.plugins.lucene.service.indexer.IFileIndexerFactory;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.search.solr.util.SolrConstants;
import fr.paris.lutece.portal.business.page.Page;
import fr.paris.lutece.portal.business.page.PageHome;
import fr.paris.lutece.portal.business.portlet.Portlet;
import fr.paris.lutece.portal.business.portlet.PortletHome;
import fr.paris.lutece.portal.service.plugin.PluginService;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.web.admin.PluginAdminPageJspBean;
import fr.paris.lutece.util.url.UrlItem;

import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;


/**
 * The indexer service for Solr.
 *
 */
public class SolrDocIndexer implements SolrIndexer
{
    // Not used
    // private static final String PARAMETER_SOLR_DOCUMENT_ID = "solr_document_id";
    private static final String PARAMETER_PORTLET_ID = "portlet_id";
    private static final String PROPERTY_INDEXER_ENABLE = "solr.indexer.document.enable";
    private static final String PROPERTY_NAME = "document-solr.indexer.name";
    private static final String PROPERTY_DESCRIPTION = "document-solr.indexer.description";
    private static final String PROPERTY_VERSION = "document-solr.indexer.version";
    private static final String PARAMETER_DOCUMENT_ID = "document_id";
    private static final String PARAMETER_ATTRIBUTE_ID = "id_attribute";
    private static final List<String> LIST_RESSOURCES_NAME = new ArrayList<String>(  );
    private static final String SHORT_NAME = "doc";
    private static final String DOC_INDEXATION_ERROR = "[SolrDocIndexer] An error occured during the indexation of the document number ";

    private static final String PARAMETER_TYPE_NUMERICTEXT = "numerictext";

    
    /**
     * Creates a new SolrPageIndexer
     */
    public SolrDocIndexer(  )
    {
        LIST_RESSOURCES_NAME.add( DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE );
    }

    @Override
    public boolean isEnable(  )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> indexDocuments(  )
    {
        List<String> lstErrors = new ArrayList<String>(  );

        //Page page;
        for ( Portlet portlet : PortletHome.findByType( DocumentListPortletHome.getInstance(  ).getPortletTypeId(  ) ) )
        {
            //page = PageHome.getPage( portlet.getPageId(  ) );
            for ( Document d : PublishingService.getInstance(  ).getPublishedDocumentsByPortletId( portlet.getId(  ) ) )
            {
                try
                {
                    //The Lucene document of plugin-document
                    Document document = DocumentHome.findByPrimaryKey( d.getId(  ) );

                    // Generates the item to index
                    SolrItem item = getItem( portlet, document );

                    if ( item != null )
                    {
                        SolrIndexerService.write( item );
                    }
                }
                catch ( Exception e )
                {
                    lstErrors.add( SolrIndexerService.buildErrorMessage( e ) );
                    AppLogService.error( DOC_INDEXATION_ERROR + d.getId(  ), e );
                }
            }
        }

        return lstErrors;
    }

    /**
     * Get item
     * @param portlet The portlet
     * @param document The document
     * @return The item
     * @throws IOException
     */
    private SolrItem getItem( Portlet portlet, Document document )
        throws IOException
    {
        // the item
        SolrItem item = new SolrItem(  );
        item.setUid( getResourceUid( Integer.valueOf( document.getId(  ) ).toString(  ),
                DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE ) );
        item.setDate( document.getDateModification(  ) );
        item.setType( document.getType(  ) );
        item.setSummary( document.getSummary(  ) );
        item.setTitle( document.getTitle(  ) );
        item.setSite( SolrIndexerService.getWebAppName(  ) );
        item.setRole( "none" );

        if ( portlet != null )
        {
            item.setDocPortletId( document.getId(  ) + SolrConstants.CONSTANT_AND + portlet.getId(  ) );
        }

        item.setXmlContent( document.getXmlValidatedContent(  ) );

        // Reload the full object to get all its searchable attributes
        UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl(  ) );
        url.addParameter( PARAMETER_DOCUMENT_ID, document.getId(  ) );
        url.addParameter( PARAMETER_PORTLET_ID, portlet.getId(  ) );
        item.setUrl( url.getUrl(  ) );

        // Date Hierarchy
        GregorianCalendar calendar = new GregorianCalendar(  );
        calendar.setTime( document.getDateModification(  ) );
        item.setHieDate( calendar.get( GregorianCalendar.YEAR ) + "/" + ( calendar.get( GregorianCalendar.MONTH ) + 1 ) +
            "/" + calendar.get( GregorianCalendar.DAY_OF_MONTH ) + "/" );

        List<String> categorie = new ArrayList<String>(  );

        for ( Category cat : document.getCategories(  ) )
        {
            categorie.add( cat.getName(  ) );
        }

        item.setCategorie( categorie );

        // The content
        String strContentToIndex = getContentToIndex( document, item );
        ContentHandler handler = new BodyContentHandler(  );
        Metadata metadata = new Metadata(  );

        try
        {
            new HtmlParser(  ).parse( new ByteArrayInputStream( strContentToIndex.getBytes(  ) ), handler, metadata,
                new ParseContext(  ) );
        }
        catch ( SAXException e )
        {
            throw new AppException( "Error during document parsing." );
        }
        catch ( TikaException e )
        {
            throw new AppException( "Error during document parsing." );
        }

        item.setContent( handler.toString(  ) );

        return item;
    }

    /**
     * GEt the content to index
     * @param document The document
     * @param item The SolR item
     * @return The content
     */
    private static String getContentToIndex( Document document, SolrItem item )
    {
        StringBuilder sbContentToIndex = new StringBuilder(  );
        sbContentToIndex.append( document.getTitle(  ) );

        for ( DocumentAttribute attribute : document.getAttributes(  ) )
        {
            if ( attribute.isSearchable(  ) )
            {
                if ( !attribute.isBinary(  ) )
                {
                    // Text attributes
                    sbContentToIndex.append( attribute.getTextValue(  ) );
                    sbContentToIndex.append( " " );

                    //Dynamic Field
                
                    if (PARAMETER_TYPE_NUMERICTEXT.equalsIgnoreCase(attribute.getCodeAttributeType()))
                    {
                    	Long nI = StringUtils.isNotEmpty(attribute.getTextValue(  )) && StringUtils.isNumeric(attribute.getTextValue(  ).trim()) ? Long.valueOf(attribute.getTextValue(  ).trim()) : 0 ;
                    	item.addDynamicField( attribute.getCode(  ), nI);
                    }
                    else	
                    	item.addDynamicField( attribute.getCode(  ), attribute.getTextValue(  ) );
                }
                else
                {
                    // Binary file attribute
                    // Gets indexer depending on the ContentType (ie: "application/pdf" should use a PDF indexer)
                    IFileIndexerFactory _factoryIndexer = (IFileIndexerFactory) SpringContextService.getBean( IFileIndexerFactory.BEAN_FILE_INDEXER_FACTORY );
                    IFileIndexer indexer = _factoryIndexer.getIndexer( attribute.getValueContentType(  ) );

                    if ( indexer != null )
                    {
                        try
                        {
                            ByteArrayInputStream bais = new ByteArrayInputStream( attribute.getBinaryValue(  ) );
                            sbContentToIndex.append( indexer.getContentToIndex( bais ) );
                            sbContentToIndex.append( " " );
                            bais.close(  );
                        }
                        catch ( IOException e )
                        {
                            AppLogService.error( e.getMessage(  ), e );
                        }
                    }
                    else
                    {
                        AppLogService.debug( "No indexer found. Url to this data will be given instead" );

                        String strName = attribute.getCode(  ) + "_" + attribute.getCodeAttributeType(  ) + "_url";
                        UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl(  ) );
                        url.addParameter( PARAMETER_DOCUMENT_ID, document.getId(  ) );
                        url.addParameter( PARAMETER_ATTRIBUTE_ID, attribute.getId(  ) );
                        item.addDynamicField( strName, url.getUrl(  ) );
                    }
                }
            }
        }

        // Index Metadata
        if ( document.getXmlMetadata(  ) != null )
        {
            sbContentToIndex.append( document.getXmlMetadata(  ) );
        }

        return sbContentToIndex.toString(  );
    }

    //GETTERS & SETTERS
    /**
     * Returns the name of the indexer.
     *
     * @return the name of the indexer
     */
    @Override
    public String getName(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_NAME );
    }

    /**
     * Returns the version.
     *
     * @return the version.
     */
    @Override
    public String getVersion(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_VERSION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_DESCRIPTION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Field> getAdditionalFields(  )
    {
        Collection<DocumentType> cAllTypes = DocumentTypeHome.findAll(  );
        List<Field> lstFields = new ArrayList<Field>(  );

        for ( DocumentType type : cAllTypes )
        {
            DocumentAttributeHome.setDocumentTypeAttributes( type );

            for ( DocumentAttribute attribute : type.getAttributes(  ) )
            {
                Field field = new Field(  );
                field.setEnableFacet( true );
                field.setDescription( attribute.getDescription(  ) );
                field.setIsFacet( true );
                field.setName( attribute.getCode(  ) + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX );
                field.setLabel( attribute.getName(  ) );

                lstFields.add( field );
            }
        }

        return lstFields;
    }

    /**
     * Builds a document which will be used by solr during the indexing of the
     * pages of the site with the following fields : summary, uid, url,
     * contents, title and description.
     *
     * @param document the document to index
     * @param strUrl the url of the documents
     * @param strRole the lutece role of the page associate to the document
     * @param strPortletDocumentId the document id concatened to the id portlet
     * with a & in the middle
     * @return the built Document
     * @throws IOException The IO Exception
     * @throws InterruptedException The InterruptedException
     */
    private SolrItem getDocument( Document document, String strUrl, String strRole, String strPortletDocumentId )
        throws IOException, InterruptedException
    {
        // make a new, empty document
        SolrItem item = new SolrItem(  );

        // Add the url as a field named "url".  Use an UnIndexed field, so
        // that the url is just stored with the document, but is not searchable.
        item.setUrl( strUrl );

        // Add the PortletDocumentId as a field named "document_portlet_id".  
        item.setDocPortletId( strPortletDocumentId );

        // Add the last modified date of the file a field named "modified".
        // Use a field that is indexed (i.e. searchable), but don't tokenize
        // the field into words.
        item.setDate( document.getDateModification(  ) );

        // Add the uid as a field, so that index can be incrementally maintained.
        // This field is not stored with document, it is indexed, but it is not
        // tokenized prior to indexing.
        String strIdDocument = String.valueOf( document.getId(  ) );
        item.setUid( getResourceUid( strIdDocument, DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE ) );

        String strContentToIndex = getContentToIndex( document, item );
        ContentHandler handler = new BodyContentHandler(  );
        Metadata metadata = new Metadata(  );

        try
        {
            new org.apache.tika.parser.html.HtmlParser(  ).parse( new ByteArrayInputStream( 
                    strContentToIndex.getBytes(  ) ), handler, metadata, new ParseContext(  ) );
        }
        catch ( SAXException e )
        {
            throw new AppException( "Error during document parsing." );
        }
        catch ( TikaException e )
        {
            throw new AppException( "Error during document parsing." );
        }

        // Add the tag-stripped contents as a Reader-valued Text field so it will
        // get tokenized and indexed.
        item.setContent( handler.toString(  ) );

        // Add the title as a separate Text field, so that it can be searched
        // separately.
        item.setTitle( document.getTitle(  ) );

        item.setType( document.getType(  ) );

        item.setRole( strRole );

        item.setSite( SolrIndexerService.getWebAppName(  ) );

        // return the document
        return item;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SolrItem> getDocuments( String strIdDocument )
    {
        List<SolrItem> lstItems = new ArrayList<SolrItem>(  );

        int nIdDocument = Integer.parseInt( strIdDocument );
        Document document = DocumentHome.findByPrimaryKey( nIdDocument );
        Iterator<Portlet> it = PublishingService.getInstance(  ).getPortletsByDocumentId( Integer.toString( nIdDocument ) )
                                                .iterator(  );

        try
        {
            while ( it.hasNext(  ) )
            {
                Portlet portlet = it.next(  );
                UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl(  ) );
                url.addParameter( PARAMETER_DOCUMENT_ID, nIdDocument );
                url.addParameter( PARAMETER_PORTLET_ID, portlet.getId(  ) );

                String strPortletDocumentId = nIdDocument + "&" + portlet.getId(  );
                Page page = PageHome.getPage( portlet.getPageId(  ) );

                lstItems.add( getDocument( document, url.getUrl(  ), page.getRole(  ), strPortletDocumentId ) );
            }
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e );
        }

        return lstItems;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getResourcesName(  )
    {
        return LIST_RESSOURCES_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getResourceUid( String strResourceId, String strResourceType )
    {
        StringBuilder sb = new StringBuilder( strResourceId );
        sb.append( SolrConstants.CONSTANT_UNDERSCORE ).append( SHORT_NAME );

        return sb.toString(  );
    }
}
