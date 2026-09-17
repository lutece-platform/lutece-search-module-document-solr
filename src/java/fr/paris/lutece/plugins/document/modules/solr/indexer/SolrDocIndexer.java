/*
 * Copyright (c) 2002-2026, City of Paris
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
import fr.paris.lutece.plugins.document.business.portlet.DocumentPortletHome;
import fr.paris.lutece.plugins.document.service.publishing.PublishingService;
import fr.paris.lutece.plugins.document.utils.DocumentIndexerUtils;
import fr.paris.lutece.plugins.document.utils.IntegerUtils;
import fr.paris.lutece.plugins.leaflet.business.GeolocItem;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.search.solr.util.SolrConstants;
import fr.paris.lutece.plugins.search.solr.util.SolrHtmlParserUtil;
import fr.paris.lutece.portal.business.page.Page;
import fr.paris.lutece.portal.business.page.PageHome;
import fr.paris.lutece.portal.business.portlet.Portlet;
import fr.paris.lutece.portal.business.portlet.PortletHome;
import fr.paris.lutece.portal.service.parser.Parser;
import fr.paris.lutece.portal.service.parser.ParserException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.url.UrlItem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;

/**
 * The indexer service for Solr.
 *
 */
@ApplicationScoped
@Named( SolrDocIndexer.BEAN_NAME )
public class SolrDocIndexer implements SolrIndexer
{
    public static final String BEAN_NAME = "document-solr.solrDocIndexer";
    // Not used
    // private static final String PARAMETER_SOLR_DOCUMENT_ID = "solr_document_id";
    private static final String PARAMETER_PORTLET_ID = "portlet_id";
    private static final String PROPERTY_INDEXER_ENABLE = "solr.indexer.document.enable";
    private static final String PROPERTY_DOCUMENT_MAX_CHARS = "document-solr.indexer.document.characters.limit";
    private static final String PROPERTY_NAME = "document-solr.indexer.name";
    private static final String PROPERTY_DESCRIPTION = "document-solr.indexer.description";
    private static final String PROPERTY_VERSION = "document-solr.indexer.version";
    private static final String PROPERTY_DOCUMENT_PORTLET_ENABLE = "document-solr.indexer.documentPortlet.enable";
    private static final String PARAMETER_DOCUMENT_ID = "document_id";
    private static final String PARAMETER_ATTRIBUTE_ID = "id_attribute";
    private static final List<String> LIST_RESSOURCES_NAME = List.of( DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE );
    private static final String SHORT_NAME = "doc";
    private static final String DOC_INDEXATION_ERROR = "[SolrDocIndexer] An error occured during the indexation of the document number ";
    private static final String NO_PARSER_MESSAGE = "[SolrDocIndexer] No parser deployed : binary document attributes are indexed by their url. Install plugin-parser to index their content.";
    private static final String AMBIGUOUS_PARSER_MESSAGE = "[SolrDocIndexer] Several Parser implementations are deployed and none takes precedence : binary document attributes are indexed by their url. Give one implementation a higher @LutecePriority.";

    private static final String PARAMETER_TYPE_NUMERICTEXT = "numerictext";
    private static final String PARAMETER_TYPE_GEOLOC = "geoloc";
    private static final String PARAMETER_TYPE_DATE = "date";

    private static final String PROPERTY_WRITER_MAX_FIELD_LENGTH = "search.lucene.writer.maxFieldLength"; // from the core
    private static final int DEFAULT_WRITER_MAX_FIELD_LENGTH = 1000000;

    @Inject
    private PublishingService _publishingService;

    @Inject
    private DocumentListPortletHome _documentListPortletHome;

    @Inject
    private DocumentPortletHome _documentPortletHome;

    @Inject
    private Instance<Parser> _parsers;

    private boolean _bNoParserLogged;

    @Override
    public boolean isEnable( )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * Return true if document portlet is enable for indexing
     * 
     * @return true if enable otherwise false
     */
    public boolean isDocumentPortletEnable( )
    {
        return Boolean.TRUE.equals( AppPropertiesService.getPropertyBoolean( PROPERTY_DOCUMENT_PORTLET_ENABLE, Boolean.FALSE ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> indexDocuments( )
    {
        List<String> lstErrors = new ArrayList<String>( );
        List<Integer> listDocument = new ArrayList<Integer>( );

        // Page page;
        List<Portlet> portletList = PortletHome.findByType( _documentListPortletHome.getPortletTypeId( ) );

        // Index document published on DocumentPortlets
        if ( isDocumentPortletEnable( ) )
        {
            portletList.addAll( PortletHome.findByType( _documentPortletHome.getPortletTypeId( ) ) );
        }

        for ( Portlet portlet : portletList )
        {
            Collection<SolrItem> solrItems = new ArrayList<SolrItem>( );

            for ( Document d : _publishingService.getPublishedDocumentsByPortletId( portlet.getId( ) ) )
            {
                try
                {
                    // The Lucene document of plugin-document
                    Document document = DocumentHome.findByPrimaryKey( d.getId( ) );

                    if ( document != null && !listDocument.contains( document.getId( ) ) )
                    {
                        // Generates the item to index
                        solrItems.add( getItem( portlet, document ) );
                        listDocument.add( document.getId( ) );
                    }
                }
                catch( Exception e )
                {
                    lstErrors.add( DOC_INDEXATION_ERROR + d.getId( ) + " : " + SolrIndexerService.buildErrorMessage( e ) );
                    AppLogService.error( "{}{}", DOC_INDEXATION_ERROR, d.getId( ), e );

                }
            }

            try
            {
                SolrIndexerService.write( solrItems );
            }
            catch( Exception e )
            {
                lstErrors.add( SolrIndexerService.buildErrorMessage( e ) );
                AppLogService.error( DOC_INDEXATION_ERROR, e );
            }
        }

        return lstErrors;
    }

    /**
     * iNDEX LIST oF DICUMENT PUBLISHED
     * 
     * @param listIdDocument
     * @return error LIST
     * @throws Exception
     */
    public List<String> indexListDocuments( Portlet portlet, List<Integer> listIdDocument ) throws Exception
    {
        List<String> lstErrors = new ArrayList<>( );
        StringBuilder sbLogs = new StringBuilder( );

        Collection<SolrItem> solrItems = new ArrayList<>( );
        for ( Integer d : listIdDocument )
        {
            Document document = DocumentHome.findByPrimaryKey( d );
            if ( document != null )
            {
                solrItems.add( getItem( portlet, document ) );
            }
        }

        try
        {
            SolrIndexerService.write( solrItems, sbLogs );

        }
        catch( Exception e )
        {
            lstErrors.add( SolrIndexerService.buildErrorMessage( e ) );
            lstErrors.add( sbLogs.toString( ) );
            AppLogService.error( DOC_INDEXATION_ERROR, e );

        }

        return lstErrors;
    }

    /**
     * Get item
     * 
     * @param portlet
     *            The portlet
     * @param document
     *            The document
     * @return The item
     * @throws IOException
     */
    private SolrItem getItem( Portlet portlet, Document document ) throws Exception
    {
        // the item
        SolrItem item = new SolrItem( );
        item.setUid( getResourceUid( Integer.valueOf( document.getId( ) ).toString( ), DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE ) );
        item.setDate( document.getDateModification( ) );
        item.setType( document.getType( ) );
        item.setSummary( document.getSummary( ) );
        item.setTitle( document.getTitle( ) );
        item.setSite( SolrIndexerService.getWebAppName( ) );
        item.setRole( "none" );

        if ( portlet != null )
        {
            item.setDocPortletId( document.getId( ) + SolrConstants.CONSTANT_AND + portlet.getId( ) );
        }

        item.setXmlContent( document.getXmlValidatedContent( ) );

        // Reload the full object to get all its searchable attributes
        UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl( ) );
        url.addParameter( PARAMETER_DOCUMENT_ID, document.getId( ) );
        url.addParameter( PARAMETER_PORTLET_ID, portlet.getId( ) );
        item.setUrl( url.getUrl( ) );

        // Date Hierarchy
        GregorianCalendar calendar = new GregorianCalendar( );
        calendar.setTime( document.getDateModification( ) );
        item.setHieDate( calendar.get( GregorianCalendar.YEAR ) + "/" + ( calendar.get( GregorianCalendar.MONTH ) + 1 ) + "/"
                + calendar.get( GregorianCalendar.DAY_OF_MONTH ) + "/" );

        List<String> categorie = new ArrayList<String>( );

        for ( Category cat : document.getCategories( ) )
        {
            categorie.add( cat.getName( ) );
        }

        item.setCategorie( categorie );

        // The content
        String strContentToIndex = getContentToIndex( document, item );
        item.setContent( truncate( SolrHtmlParserUtil.parseHtml( strContentToIndex ), getMaxChars( ) ) );

        return item;
    }

    /**
     * Returns the maximum number of characters indexed for a document content.
     *
     * @return the limit, or a negative value when the content is not limited
     */
    private static int getMaxChars( )
    {
        String strMaxChars = AppPropertiesService.getProperty( PROPERTY_DOCUMENT_MAX_CHARS );

        if ( StringUtils.isNotBlank( strMaxChars ) )
        {
            return Integer.parseInt( strMaxChars );
        }

        return AppPropertiesService.getPropertyInt( PROPERTY_WRITER_MAX_FIELD_LENGTH, DEFAULT_WRITER_MAX_FIELD_LENGTH );
    }

    /**
     * Truncates a content to a maximum number of characters.
     *
     * @param strContent
     *            the content
     * @param nMaxChars
     *            the maximum number of characters, ignored when not strictly positive
     * @return the truncated content
     */
    private static String truncate( String strContent, int nMaxChars )
    {
        if ( ( strContent == null ) || ( nMaxChars <= 0 ) || ( strContent.length( ) <= nMaxChars ) )
        {
            return strContent;
        }

        return strContent.substring( 0, nMaxChars );
    }

    /**
     * Returns the parser used to extract the text of a binary document attribute.
     *
     * @return the parser, or null when no implementation is deployed
     */
    private Parser getParser( )
    {
        if ( _parsers.isResolvable( ) )
        {
            return _parsers.get( );
        }

        if ( !_bNoParserLogged )
        {
            AppLogService.info( _parsers.isUnsatisfied( ) ? NO_PARSER_MESSAGE : AMBIGUOUS_PARSER_MESSAGE );
            _bNoParserLogged = true;
        }

        return null;
    }

    /**
     * GEt the content to index
     * 
     * @param document
     *            The document
     * @param item
     *            The SolR item
     * @return The content
     */
    private String getContentToIndex( Document document, SolrItem item )
    {
        StringBuilder sbContentToIndex = new StringBuilder( );
        sbContentToIndex.append( document.getTitle( ) );
        sbContentToIndex.append( " " );

        Parser parser = getParser( );

        for ( DocumentAttribute attribute : document.getAttributes( ) )
        {
            if ( attribute.isSearchable( ) )
            {
                if ( !attribute.isBinary( ) )
                {
                    if ( PARAMETER_TYPE_GEOLOC.equalsIgnoreCase( attribute.getCodeAttributeType( ) ) )
                    {
                        // Geojson attribute, put the address as text if it exists
                        String address = null;
                        GeolocItem geolocItem = null;
                        try
                        {
                            geolocItem = GeolocItem.fromJSON( attribute.getTextValue( ) );
                        }
                        catch( IOException e )
                        {
                            AppLogService.error( "SolrDocumentIndexer, error parsing JSON {}", e.getMessage( ), e );
                        }
                        if ( geolocItem != null && geolocItem.getAddress( ) != null )
                        {
                            sbContentToIndex.append( geolocItem.getAddress( ) );
                        }
                    }
                    else
                    {
                        // Text attributes
                        sbContentToIndex.append( attribute.getTextValue( ) );
                    }
                    sbContentToIndex.append( " " );

                    // Dynamic Field

                    if ( PARAMETER_TYPE_NUMERICTEXT.equalsIgnoreCase( attribute.getCodeAttributeType( ) ) )
                    {
                        Long nI = StringUtils.isNotEmpty( attribute.getTextValue( ) ) && StringUtils.isNumeric( attribute.getTextValue( ).trim( ) )
                                ? Long.valueOf( attribute.getTextValue( ).trim( ) )
                                : 0;
                        item.addDynamicField( attribute.getCode( ), nI );
                    }
                    else
                        if ( PARAMETER_TYPE_GEOLOC.equalsIgnoreCase( attribute.getCodeAttributeType( ) ) )
                        {
                            item.addDynamicFieldGeoloc( attribute.getCode( ), attribute.getTextValue( ), document.getCodeDocumentType( ) );
                        }
                        else
                            if ( PARAMETER_TYPE_DATE.equalsIgnoreCase( attribute.getCodeAttributeType( ) ) && !"".equals( attribute.getTextValue( ) ) )
                            {
                                // Todo : how to ensure using the right date format ?
                                DateFormat format = new SimpleDateFormat( "dd/MM/yyyy" );
                                try
                                {
                                    Date date = format.parse( attribute.getTextValue( ) );
                                    item.addDynamicField( attribute.getCode( ), date );
                                }
                                catch( ParseException e )
                                {
                                    AppLogService.error( e.getMessage( ), e );
                                }
                            }
                            else
                                item.addDynamicField( attribute.getCode( ), attribute.getTextValue( ) );
                }
                else
                {
                    // Binary file attribute
                    // The deployed parser handles the ContentType (ie: plugin-parser indexes "application/pdf")
                    boolean bIndexed = false;

                    if ( parser != null )
                    {
                        try ( ByteArrayInputStream bais = new ByteArrayInputStream( attribute.getBinaryValue( ) ) )
                        {
                            sbContentToIndex.append( parser.parseToString( bais ) );
                            sbContentToIndex.append( " " );
                            bIndexed = true;
                        }
                        catch( ParserException | IOException e )
                        {
                            AppLogService.error( e.getMessage( ), e );
                        }
                    }

                    if ( !bIndexed )
                    {
                        AppLogService.debug( "No indexer found. Url to this data will be given instead" );

                        String strName = attribute.getCode( ) + "_" + attribute.getCodeAttributeType( ) + "_url";
                        UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl( ) );
                        url.addParameter( PARAMETER_DOCUMENT_ID, document.getId( ) );
                        url.addParameter( PARAMETER_ATTRIBUTE_ID, attribute.getId( ) );
                        item.addDynamicField( strName, url.getUrl( ) );
                    }
                }
            }
        }

        // Index Metadata
        if ( document.getXmlMetadata( ) != null )
        {
            sbContentToIndex.append( document.getXmlMetadata( ) );
        }

        return sbContentToIndex.toString( );
    }

    // GETTERS & SETTERS
    /**
     * Returns the name of the indexer.
     *
     * @return the name of the indexer
     */
    @Override
    public String getName( )
    {
        return AppPropertiesService.getProperty( PROPERTY_NAME );
    }

    /**
     * Returns the version.
     *
     * @return the version.
     */
    @Override
    public String getVersion( )
    {
        return AppPropertiesService.getProperty( PROPERTY_VERSION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription( )
    {
        return AppPropertiesService.getProperty( PROPERTY_DESCRIPTION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Field> getAdditionalFields( )
    {
        Collection<DocumentType> cAllTypes = DocumentTypeHome.findAll( );
        List<Field> lstFields = new ArrayList<Field>( );

        for ( DocumentType type : cAllTypes )
        {
            DocumentAttributeHome.setDocumentTypeAttributes( type );

            for ( DocumentAttribute attribute : type.getAttributes( ) )
            {
                Field field = new Field( );
                field.setEnableFacet( true );
                field.setDescription( attribute.getDescription( ) );
                field.setIsFacet( true );
                field.setName( attribute.getCode( ) + SolrItem.DYNAMIC_TEXT_FIELD_SUFFIX );
                field.setLabel( attribute.getName( ) );

                lstFields.add( field );
            }
        }

        return lstFields;
    }

    /**
     * Builds a document which will be used by solr during the indexing of the pages of the site with the following fields : summary, uid, url, contents, title
     * and description.
     *
     * @param document
     *            the document to index
     * @param strUrl
     *            the url of the documents
     * @param strRole
     *            the lutece role of the page associate to the document
     * @param strPortletDocumentId
     *            the document id concatened to the id portlet with a & in the middle
     * @return the built Document
     * @throws IOException
     *             The IO Exception
     * @throws InterruptedException
     *             The InterruptedException
     */
    private SolrItem getDocument( Document document, String strUrl, String strRole, String strPortletDocumentId ) throws IOException, InterruptedException
    {
        // make a new, empty document
        SolrItem item = new SolrItem( );

        // Add the url as a field named "url". Use an UnIndexed field, so
        // that the url is just stored with the document, but is not searchable.
        item.setUrl( strUrl );

        // Add the PortletDocumentId as a field named "document_portlet_id".
        item.setDocPortletId( strPortletDocumentId );

        // Add the last modified date of the file a field named "modified".
        // Use a field that is indexed (i.e. searchable), but don't tokenize
        // the field into words.
        item.setDate( document.getDateModification( ) );

        // Add the uid as a field, so that index can be incrementally maintained.
        // This field is not stored with document, it is indexed, but it is not
        // tokenized prior to indexing.
        String strIdDocument = String.valueOf( document.getId( ) );
        item.setUid( getResourceUid( strIdDocument, DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE ) );

        String strContentToIndex = getContentToIndex( document, item );

        // Add the tag-stripped contents as a Reader-valued Text field so it will
        // get tokenized and indexed.
        item.setContent( truncate( SolrHtmlParserUtil.parseHtml( strContentToIndex ), getMaxChars( ) ) );

        // Add the title as a separate Text field, so that it can be searched
        // separately.
        item.setTitle( document.getTitle( ) );

        item.setType( document.getType( ) );

        item.setRole( strRole );

        item.setSite( SolrIndexerService.getWebAppName( ) );

        // return the document
        return item;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SolrItem> getDocuments( String strIdDocument )
    {
        if ( !IntegerUtils.isNumeric( strIdDocument ) )
        {
            AppLogService.error( "[SolrDocIndexer] Not a document identifier : {}", strIdDocument );

            return Collections.emptyList( );
        }

        int nIdDocument = IntegerUtils.convert( strIdDocument );
        Document document = DocumentHome.findByPrimaryKey( nIdDocument );

        if ( document == null )
        {
            AppLogService.debug( "[SolrDocIndexer] Unknown document, nothing to index : {}", nIdDocument );

            return Collections.emptyList( );
        }

        List<SolrItem> lstItems = new ArrayList<SolrItem>( );
        Iterator<Portlet> it = _publishingService.getPortletsByDocumentId( Integer.toString( nIdDocument ) ).iterator( );

        try
        {
            while ( it.hasNext( ) )
            {
                Portlet portlet = it.next( );

                if ( portlet == null )
                {
                    AppLogService.debug( "[SolrDocIndexer] A publication of the document {} refers to a portlet that could not be loaded, skipped",
                            nIdDocument );

                    continue;
                }

                UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl( ) );
                url.addParameter( PARAMETER_DOCUMENT_ID, nIdDocument );
                url.addParameter( PARAMETER_PORTLET_ID, portlet.getId( ) );

                String strPortletDocumentId = nIdDocument + "&" + portlet.getId( );
                Page page = PageHome.getPage( portlet.getPageId( ) );

                if ( page == null )
                {
                    AppLogService.debug( "[SolrDocIndexer] The portlet {} refers to the unknown page {}, skipped", portlet.getId( ), portlet.getPageId( ) );

                    continue;
                }

                lstItems.add( getDocument( document, url.getUrl( ), page.getRole( ), strPortletDocumentId ) );
            }
        }
        catch( Exception e )
        {
            throw new RuntimeException( e );
        }

        return lstItems;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getResourcesName( )
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

        return sb.toString( );
    }
}
