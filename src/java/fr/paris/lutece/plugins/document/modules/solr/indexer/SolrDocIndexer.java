/*
 * Copyright (c) 2002-2009, Mairie de Paris
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.demo.html.HTMLParser;

import fr.paris.lutece.plugins.document.business.Document;
import fr.paris.lutece.plugins.document.business.DocumentHome;
import fr.paris.lutece.plugins.document.business.DocumentType;
import fr.paris.lutece.plugins.document.business.DocumentTypeHome;
import fr.paris.lutece.plugins.document.business.attributes.DocumentAttribute;
import fr.paris.lutece.plugins.document.business.attributes.DocumentAttributeHome;
import fr.paris.lutece.plugins.document.business.category.Category;
import fr.paris.lutece.plugins.document.business.portlet.DocumentListPortletHome;
import fr.paris.lutece.plugins.document.service.publishing.PublishingService;
import fr.paris.lutece.plugins.lucene.service.indexer.IFileIndexer;
import fr.paris.lutece.plugins.lucene.service.indexer.IFileIndexerFactory;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.portal.business.portlet.Portlet;
import fr.paris.lutece.portal.business.portlet.PortletHome;
import fr.paris.lutece.portal.service.spring.SpringContextService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.url.UrlItem;


/**
 * The indexer service for Solr.
 *
 */
public class SolrDocIndexer implements SolrIndexer
{
    private static final String PROPERTY_PAGE_BASE_URL = "document.documentIndexer.baseUrl";
    private static final String SITE_URL = AppPropertiesService.getProperty( PROPERTY_PAGE_BASE_URL );
    private static final String PARAMETER_SOLR_DOCUMENT_ID = "solr_document_id";
    private static final String PARAMETER_PORTLET_ID = "portlet_id";
    private static final String PROPERTY_INDEXER_ENABLE = "solr.indexer.document.enable";
    private static final String PROPERTY_NAME = "document-solr.indexer.name";
    private static final String PROPERTY_DESCRIPTION = "document-solr.indexer.description";
    private static final String PROPERTY_VERSION = "document-solr.indexer.version";
    private static final String PROPERTY_SHORT_NAME = "document-solr.indexer.version";
    private static final String PROPERTY_SITE = "solr.site.name";
    
	private static final String PARAMETER_DOCUMENT_ID = "id";
	private static final String PARAMETER_ATTRIBUTE_ID = "id_attribute";
	private static final String DOCUMENT_ROOT_URL = "@base_url@document";

    /**
     * Creates a new SolrPageIndexer
     */
    public SolrDocIndexer(  )
    {
    }

    public boolean isEnable(  )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * Indexes data.
     * @return mapDatas Map wich associates logs to indexed datas.
     */
	public Map<String, SolrItem> index(  )
    {
        StringBuilder sbLogs = new StringBuilder(  );
        Map<String, SolrItem> mapDatas = new HashMap<String, SolrItem>();
        
        //Page page;
        for ( Portlet portlet : PortletHome.findByType( DocumentListPortletHome.getInstance(  ).getPortletTypeId(  ) ) )
        {
            //page = PageHome.getPage( portlet.getPageId(  ) );
            for ( Document d : PublishingService.getInstance(  ).getPublishedDocumentsByPortletId( portlet.getId(  ) ) )
            {
                //The Lucene document of plugin-document
                Document document = DocumentHome.findByPrimaryKey( d.getId(  ) );

                try
                {
                	// Clears the buffer
                	sbLogs.setLength( 0 );
                	// Adds the log
                    sbLogs.append( "indexing " );
                    sbLogs.append( document.getType(  ) );
                    sbLogs.append( " id : " );
                    sbLogs.append( document.getId(  ) );
                    sbLogs.append( " Title : " );
                    sbLogs.append( document.getTitle(  ) );
                    sbLogs.append( "<br/>" );
                    
                    // Generates the item to index
                    SolrItem item = getItem( portlet, document );
                    mapDatas.put( sbLogs.toString(), item );
                }
                catch ( IOException e )
                {
                	AppLogService.error( e );
                }
            }
        }
        
        return mapDatas;
    }

	/*
    private SolrItem fillSolrItemForDocument( Document document )
        throws IOException
    {
        // the item
        SolrItem item = new SolrItem(  );
        item.setUid( Integer.valueOf( document.getId(  ) ).toString(  ) );
        item.setDate( document.getDateModification(  ) );
        item.setType( document.getType(  ) );
        item.setSummary( document.getSummary(  ) );
        item.setTitle( document.getTitle(  ) );
        item.setSite( SITE );
        item.setRole( "none" );
        item.setXmlContent( document.getXmlValidatedContent(  ) );

        //Date Hierarchy
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

        //The content
        String strContentToIndex = getContentToIndex( document, item );
        StringReader readerPage = new StringReader( strContentToIndex );
        HTMLParser parser = new HTMLParser( readerPage );

        Reader reader = parser.getReader(  );
        int c;
        StringBuffer sb = new StringBuffer(  );

        while ( ( c = reader.read(  ) ) != -1 )
        {
            sb.append( String.valueOf( (char) c ) );
        }

        reader.close(  );
        item.setContent( sb.toString(  ) );

        return item;
    }

    private SolrInputDocument fillSolrInputDocumentForDocument( Portlet portlet, Document document )
        throws IOException
    {
        // the item
        SolrInputDocument doc = new SolrInputDocument(  );
        doc.addField( SearchItem.FIELD_UID, Integer.valueOf( document.getId(  ) ).toString(  ) );
        doc.addField( SearchItem.FIELD_DATE, document.getDateModification(  ) );
        doc.addField( SearchItem.FIELD_TYPE, document.getType(  ) );
        doc.addField( SearchItem.FIELD_SUMMARY, document.getSummary(  ) );
        doc.addField( SearchItem.FIELD_TITLE, document.getTitle(  ) );
        doc.addField( SolrItem.FIELD_SITE, SITE );
        doc.addField( SearchItem.FIELD_ROLE, "none" );
        doc.addField( SolrItem.FIELD_XML_CONTENT, document.getXmlValidatedContent(  ) );

        // Reload the full object to get all its searchable attributes
        UrlItem url = new UrlItem( SITE_URL );
        url.addParameter( PARAMETER_SOLR_DOCUMENT_ID, document.getId(  ) );
        url.addParameter( PARAMETER_PORTLET_ID, portlet.getId(  ) );
        //item.setUrl( url.getUrl(  ) );
        doc.addField( SearchItem.FIELD_URL, url.getUrl(  ) );

        //Date Hierarchy
        GregorianCalendar calendar = new GregorianCalendar(  );
        calendar.setTime( document.getDateModification(  ) );
        doc.addField( SolrItem.FIELD_HIERATCHY_DATE,
            calendar.get( GregorianCalendar.YEAR ) + "/" + ( calendar.get( GregorianCalendar.MONTH ) + 1 ) + "/" +
            calendar.get( GregorianCalendar.DAY_OF_MONTH ) + "/" );

        List<String> categorie = new ArrayList<String>(  );

        for ( Category cat : document.getCategories(  ) )
        {
            categorie.add( cat.getName(  ) );
        }

        doc.addField( SolrItem.FIELD_CATEGORIE, categorie );

        //The content
        String strContentToIndex = getContentToIndex( document, doc );
        StringReader readerPage = new StringReader( strContentToIndex );
        HTMLParser parser = new HTMLParser( readerPage );

        Reader reader = parser.getReader(  );
        int c;
        StringBuffer sb = new StringBuffer(  );

        while ( ( c = reader.read(  ) ) != -1 )
        {
            sb.append( String.valueOf( (char) c ) );
        }

        reader.close(  );
        doc.addField( SolrItem.FIELD_CONTENT, sb.toString(  ) );

        return doc;
    }
    
    private static String getContentToIndex( Document document, SolrInputDocument item )
    {
        StringBuffer sbContentToIndex = new StringBuffer(  );
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
                    item.addField( attribute.getCode(  ) + "_" + attribute.getCodeAttributeType(  ),
                        attribute.getTextValue(  ) );
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
                        UrlItem url = new UrlItem( DOCUMENT_ROOT_URL  );
                        url.addParameter( PARAMETER_DOCUMENT_ID, document.getId(  ) );
                        url.addParameter( PARAMETER_ATTRIBUTE_ID, attribute.getId(  ) );
                        item.addField( strName, url.getUrl(  ) );
                    }
                }
            }
            else
            {
                if ( attribute.getName(  ).equalsIgnoreCase( "boost" ) )
                {
                    if ( ( attribute.getTextValue(  ) != null ) && !"".equals( attribute.getTextValue(  ) ) )
                    {
                        item.setDocumentBoost( Float.parseFloat( attribute.getTextValue(  ) ) );
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
    */
	
	private SolrItem getItem( Portlet portlet, Document document ) throws IOException
	{
		// the item
		SolrItem item = new SolrItem();
		item.setUid( Integer.valueOf( document.getId() ).toString() );
		item.setDate( document.getDateModification() );
		item.setType( document.getType() );
		item.setSummary( document.getSummary() );
		item.setTitle( document.getTitle() );
		item.setSite( AppPropertiesService.getProperty( PROPERTY_SITE ) );
		item.setRole( "none" );
		item.setXmlContent( document.getXmlValidatedContent() );

		// Reload the full object to get all its searchable attributes
		UrlItem url = new UrlItem( SITE_URL );
		url.addParameter( PARAMETER_SOLR_DOCUMENT_ID, document.getId() );
		url.addParameter( PARAMETER_PORTLET_ID, portlet.getId() );
		// item.setUrl( url.getUrl( ) );
		item.setUrl( url.getUrl() );

		// Date Hierarchy
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTime( document.getDateModification() );
		item.setHieDate( calendar.get( GregorianCalendar.YEAR ) + "/" + ( calendar.get( GregorianCalendar.MONTH ) + 1 ) + "/"
				+ calendar.get( GregorianCalendar.DAY_OF_MONTH ) + "/" );

		List<String> categorie = new ArrayList<String>();

		for ( Category cat : document.getCategories() )
		{
			categorie.add( cat.getName() );
		}

		item.setCategorie( categorie );

		// The content
		String strContentToIndex = getContentToIndex( document, item );
		StringReader readerPage = new StringReader( strContentToIndex );
		HTMLParser parser = new HTMLParser( readerPage );

		Reader reader = parser.getReader();
		int c;
		StringBuffer sb = new StringBuffer();

		while (( c = reader.read() ) != -1)
		{
			sb.append( String.valueOf( ( char ) c ) );
		}

		reader.close();
		item.setContent( sb.toString() );

		return item;
	}

    private static String getContentToIndex( Document document, SolrItem item )
    {
        StringBuffer sbContentToIndex = new StringBuffer(  );
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
                        UrlItem url = new UrlItem( DOCUMENT_ROOT_URL  );
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
     * @return the name of the indexer
     */
    public String getName(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_NAME );
    }

    /**
     * Returns the version.
     * @return the version.
     */
    public String getVersion(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_VERSION );
    }

    /**
     * {@inheritDoc}
     */
    public String getDescription(  )
    {
        return AppPropertiesService.getProperty( PROPERTY_DESCRIPTION );
    }

    /**
     * {@inheritDoc}
     */
	public List<Field> getAdditionalFields()
	{
		Collection<DocumentType> cAllTypes = DocumentTypeHome.findAll();
		List<Field> lstFields = new ArrayList<Field>();
		for( DocumentType type : cAllTypes )
		{
			DocumentAttributeHome.setDocumentTypeAttributes( type );
			for( DocumentAttribute attribute : type.getAttributes() )
			{
				Field field = new Field();
				field.setEnableFacet( true );
				field.setDescription( attribute.getDescription() );
				field.setIsFacet( true );
				field.setName( attribute.getCode() + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX );
				field.setLabel( attribute.getName() );
				
				lstFields.add( field );
			}
		}
		
		return lstFields;
	}
}
