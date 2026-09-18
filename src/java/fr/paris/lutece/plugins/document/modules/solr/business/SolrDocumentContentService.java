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
package fr.paris.lutece.plugins.document.modules.solr.business;

import fr.paris.lutece.plugins.document.business.Document;
import fr.paris.lutece.plugins.document.business.DocumentHome;
import fr.paris.lutece.plugins.document.business.DocumentType;
import fr.paris.lutece.plugins.document.business.DocumentTypeHome;
import fr.paris.lutece.plugins.document.business.portlet.DocumentListPortletHome;
import fr.paris.lutece.plugins.document.business.publication.DocumentPublication;
import fr.paris.lutece.plugins.document.modules.solr.indexer.SolrDocIndexer;
import fr.paris.lutece.plugins.document.service.publishing.PublishingService;
import fr.paris.lutece.plugins.document.utils.DocumentIndexerUtils;
import fr.paris.lutece.plugins.document.utils.IntegerUtils;
import fr.paris.lutece.plugins.resource.loader.ResourceNotFoundException;
import fr.paris.lutece.plugins.search.solr.business.SolrSearchEngine;
import fr.paris.lutece.plugins.search.solr.util.SolrConstants;
import fr.paris.lutece.portal.business.page.Page;
import fr.paris.lutece.portal.business.page.PageHome;
import fr.paris.lutece.portal.business.portlet.AliasPortlet;
import fr.paris.lutece.portal.business.portlet.AliasPortletHome;
import fr.paris.lutece.portal.business.portlet.Portlet;
import fr.paris.lutece.portal.business.portlet.PortletHome;
import fr.paris.lutece.portal.business.style.ModeHome;
import fr.paris.lutece.portal.service.cache.Lutece107Cache;
import fr.paris.lutece.portal.service.cache.LuteceCacheManager;
import fr.paris.lutece.portal.service.content.ContentService;
import fr.paris.lutece.portal.service.content.PageData;
import fr.paris.lutece.portal.service.html.XmlTransformerService;
import fr.paris.lutece.portal.service.message.SiteMessageException;
import fr.paris.lutece.portal.service.portal.PortalService;
import fr.paris.lutece.portal.service.security.LuteceUser;
import fr.paris.lutece.portal.service.security.SecurityService;
import fr.paris.lutece.portal.service.security.UserNotSignedException;
import fr.paris.lutece.portal.service.template.AppTemplateService;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPathService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.portal.web.constants.Parameters;
import fr.paris.lutece.util.ReferenceList;
import fr.paris.lutece.util.UniqueIDGenerator;
import fr.paris.lutece.util.date.DateUtil;
import fr.paris.lutece.util.html.HtmlTemplate;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.servlet.http.HttpServletRequest;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

/**
 *
 */
public class SolrDocumentContentService extends ContentService
{
    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Constants
    private static final String CONTENT_SERVICE_NAME = "SOLR Document Content Service";
    private static final String SLASH = "/";
    private static final int MODE_ADMIN = 1;
    private static final String CONSTANT_DEFAULT_PORTLET_DOCUMENT_LIST_XSL = "WEB-INF/xsl/normal/portlet_document_list.xsl";

    // Parameters
    private static final String PARAMETER_SOLR_DOCUMENT_ID = "solr_document_id";
    private static final String PARAMETER_SITE_PATH = "site-path";
    private static final String PARAMETER_PUBLICATION_DATE = "publication-date";
    private static final String PARAMETER_TERMS = "terms";

    // Markers
    private static final String MARK_PUBLICATION = "publication";
    private static final String MARK_DOCUMENT = "document";
    private static final String MARK_PORTLET = "portlet";
    private static final String MARK_CATEGORY = "categories";
    private static final String MARK_DOCUMENT_ID = "document_id";
    private static final String MARK_PORTLET_ID = "portlet_id";
    private static final String MARK_PORTLET_ID_LIST = "portlet_id_list";
    private static final String MARK_DOCUMENT_CATEGORIES_LIST = "document_categories_list";
    private static final String MARK_URL_LOGIN = "url_login";
    private static final String MARKER_TARGET = "target";

    // Templates
    private static final String TEMPLATE_DOCUMENT_PAGE_DEFAULT = "/skin/plugins/document/document_content_service.html";
    private static final String TEMPLATE_DOCUMENT_CATEGORIES = "/skin/plugins/document/document_categories.html";

    // Properties
    private static final String PROPERTY_DEFAULT_PORTLET_DOCUMENT_LIST_XSL = "document.contentService.defaultPortletDocumentListXSL";
    private static final String PROPERTY_CACHE_ENABLED = "document.cache.enabled";
    private static final String XSLSOURCE_STYLE_PREFIX_ID = UniqueIDGenerator.getNewId( );
    private static final String TARGET_TOP = "target=_top";

    private XmlTransformerService _xmlTransformerService;
    private PublishingService _publishingService;
    private SolrDocIndexer _solrDocIndexer;
    private SolrSearchEngine _solrSearchEngine;
    private Lutece107Cache<String, String> _cache;
    private volatile boolean _bInit;

    /**
     * Returns the document page for a given document and a given portlet. The page is built from XML data or retrieved from the cache if it's enable and the
     * document in it.
     *
     * @param request
     *            The HTTP request.
     * @param nMode
     *            The current mode.
     * @return The HTML code of the page as a String.
     * @throws UserNotSignedException
     * @throws SiteMessageException
     *             occurs when a site message need to be displayed
     */
    public String getPage( HttpServletRequest request, int nMode ) throws UserNotSignedException, SiteMessageException
    {
        if ( !_bInit )
        {
            init( );
        }

        String strDocumentId = request.getParameter( PARAMETER_SOLR_DOCUMENT_ID );
        String strPortletId = request.getParameter( Parameters.PORTLET_ID );
        String strTerms = request.getParameter( PARAMETER_TERMS );
        String strKey = getCacheKey( strDocumentId, strPortletId, strTerms, nMode );
        String strPage = _cache.isCacheEnable( ) ? _cache.get( strKey ) : null;

        if ( strPage == null )
        {
            strPage = buildPage( request, strDocumentId, strPortletId, strTerms, nMode );

            if ( IntegerUtils.isNumeric( strDocumentId ) )
            {
                int nDocumentId = IntegerUtils.convert( strDocumentId );
                Document document = DocumentHome.findByPrimaryKeyWithoutBinaries( nDocumentId );

                if ( document != null && _cache.isCacheEnable( ) )
                {
                    _cache.put( strKey, strPage );
                }
            }
        }

        return strPage;
    }

    /**
     * Initializes the service. Runs once : concurrent callers return as soon as the first one is done.
     */
    private synchronized void init( )
    {
        if ( _bInit )
        {
            return;
        }

        _publishingService = CDI.current( ).select( PublishingService.class ).get( );
        _solrDocIndexer = CDI.current( ).select( SolrDocIndexer.class ).get( );
        _solrSearchEngine = CDI.current( ).select( SolrSearchEngine.class ).get( );

        LuteceCacheManager cacheManager = CDI.current( ).select( LuteceCacheManager.class ).get( );
        _cache = cacheManager.createCache( getName( ), String.class, String.class );

        // Initialize the cache according property value.
        // If the property isn't found the default is true
        String strCache = AppPropertiesService.getProperty( PROPERTY_CACHE_ENABLED, "true" );

        _cache.enableCache( strCache.equalsIgnoreCase( "true" ) );

        _xmlTransformerService = new XmlTransformerService( );

        _bInit = true;
    }

    /**
     * Build the cache key
     * 
     * @param strDocumentId
     *            The document ID
     * @param strPortletId
     *            The portlet ID
     * @param nMode
     *            The current mode
     * @return The key
     */
    private String getCacheKey( String strDocumentId, String strPortletId, String terms, int nMode )
    {
        return "D" + strDocumentId + "P" + strPortletId + "T" + terms + "M" + nMode;
    }

    /**
     * Build the document page
     * 
     * @param request
     *            The HTTP Request
     * @param strDocumentId
     *            The document ID
     * @param strPortletId
     *            The portlet ID
     * @param nMode
     *            The current mode
     * @return
     * @throws fr.paris.lutece.portal.service.security.UserNotSignedException
     * @throws fr.paris.lutece.portal.service.message.SiteMessageException
     */
    private String buildPage( HttpServletRequest request, String strDocumentId, String strPortletId, String terms, int nMode )
            throws UserNotSignedException, SiteMessageException
    {
        int nPortletId;
        int nDocumentId;
        boolean bPortletExist = false;
        Map<String, String> mapXslParams = new HashMap<String, String>( );

        try
        {
            nPortletId = Integer.parseInt( strPortletId );
            nDocumentId = Integer.parseInt( strDocumentId );
        }
        catch( NumberFormatException nfe )
        {
            return PortalService.getDefaultPage( request, nMode );
        }

        Document document = DocumentHome.findByPrimaryKeyWithoutBinaries( nDocumentId );

        if ( ( document == null ) || ( !document.isValid( ) ) )
        {
            return PortalService.getDefaultPage( request, nMode );
        }

        DocumentType type = DocumentTypeHome.findByPrimaryKey( document.getCodeDocumentType( ) );
        DocumentPublication documentPublication = _publishingService.getDocumentPublication( nPortletId, nDocumentId );

        Map<String, Object> model = new HashMap<String, Object>( );

        if ( documentPublication != null )
        {
            // Check if portlet is an alias portlet
            boolean bIsAlias = DocumentListPortletHome.checkIsAliasPortlet( documentPublication.getPortletId( ) );

            if ( bIsAlias && ( documentPublication.getPortletId( ) != nPortletId ) )
            {
                AliasPortlet alias = (AliasPortlet) AliasPortletHome.findByPrimaryKey( nPortletId );

                if ( alias == null )
                {
                    return PortalService.getDefaultPage( request, nMode );
                }

                nPortletId = alias.getAliasId( );
                strPortletId = Integer.toString( nPortletId );
            }

            if ( ( documentPublication.getPortletId( ) == nPortletId ) && ( documentPublication.getStatus( ) == DocumentPublication.STATUS_PUBLISHED ) )
            {
                bPortletExist = true;
            }

            // The publication informations are available in Xsl (only publication date) and in template (full DocumentPublication object)
            mapXslParams.put( PARAMETER_PUBLICATION_DATE, DateUtil.getDateString( documentPublication.getDatePublishing( ), request.getLocale( ) ) );
            model.put( MARK_PUBLICATION, documentPublication );
        }

        if ( bPortletExist )
        {
            // Fill a PageData structure for those elements
            PageData data = new PageData( );
            data.setName( document.getTitle( ) );
            data.setPagePath( PortalService.getXPagePathContent( document.getTitle( ), 0, request ) );

            Portlet portlet = PortletHome.findByPrimaryKey( nPortletId );

            if ( portlet == null )
            {
                return PortalService.getDefaultPage( request, nMode );
            }

            Page page = PageHome.getPage( portlet.getPageId( ) );

            if ( page == null )
            {
                return PortalService.getDefaultPage( request, nMode );
            }

            String strRole = page.getRole( );

            if ( !strRole.equals( Page.ROLE_NONE ) && SecurityService.isAuthenticationEnable( ) )
            {
                LuteceUser user = SecurityService.getInstance( ).getRegisteredUser( request );

                if ( ( user == null ) && ( !SecurityService.getInstance( ).isExternalAuthentication( ) ) )
                {
                    // The user is not registered and identify itself with the Portal authentication
                    String strAccessControledTemplate = SecurityService.getInstance( ).getAccessControledTemplate( );
                    HashMap<String, Object> modelAccessControledTemplate = new HashMap<String, Object>( );
                    String strLoginUrl = SecurityService.getInstance( ).getLoginPageUrl( );
                    modelAccessControledTemplate.put( MARK_URL_LOGIN, strLoginUrl );

                    HtmlTemplate tAccessControled = AppTemplateService.getTemplate( strAccessControledTemplate, request.getLocale( ),
                            modelAccessControledTemplate );
                    data.setContent( tAccessControled.getHtml( ) );

                    return PortalService.buildPageContent( data, nMode, request );
                }

                if ( !SecurityService.getInstance( ).isUserInRole( request, strRole ) )
                {
                    // The user doesn't have the correct role
                    String strAccessDeniedTemplate = SecurityService.getInstance( ).getAccessDeniedTemplate( );
                    HtmlTemplate tAccessDenied = AppTemplateService.getTemplate( strAccessDeniedTemplate, request.getLocale( ) );
                    data.setContent( tAccessDenied.getHtml( ) );

                    return PortalService.buildPageContent( data, nMode, request );
                }
            }

            String xmlContent = _solrSearchEngine
                    .getDocumentHighLighting( _solrDocIndexer.getResourceUid( strDocumentId, DocumentIndexerUtils.CONSTANT_TYPE_RESOURCE ), terms );

            if ( xmlContent == null )
            {
                xmlContent = document.getXmlValidatedContent( );
            }

            String strDocument = _xmlTransformerService.transformBySourceWithXslCache( xmlContent, type.getContentServiceXslSource( ),
                    XSLSOURCE_STYLE_PREFIX_ID + type.getCode( ), mapXslParams, null );

            model.put( MARK_DOCUMENT, strDocument );
            model.put( MARK_PORTLET, getPortlet( request, strPortletId, nMode ) );
            model.put( MARK_CATEGORY, getRelatedDocumentsPortlet( request, document, nPortletId, nMode ) );
            model.put( MARK_DOCUMENT_ID, strDocumentId );
            model.put( MARK_PORTLET_ID, strPortletId );

            HtmlTemplate template = AppTemplateService.getTemplate( getTemplatePage( document ), request.getLocale( ), model );

            data.setContent( template.getHtml( ) );

            return PortalService.buildPageContent( data, nMode, request );
        }
        else // portlet does not exists
        {
            // TODO : view access denied page
            return PortalService.getDefaultPage( request, nMode );
        }
    }

    /**
     * Analyzes request parameters to see if the request should be handled by the current Content Service
     *
     * @param request
     *            The HTTP request
     * @return true if this ContentService should handle this request
     */
    public boolean isInvoked( HttpServletRequest request )
    {
        String strDocumentId = request.getParameter( PARAMETER_SOLR_DOCUMENT_ID );
        String strIdPortlet = request.getParameter( Parameters.PORTLET_ID );
        String strTerms = request.getParameter( PARAMETER_TERMS );

        if ( ( strDocumentId != null ) && ( strDocumentId.length( ) > 0 ) && ( strIdPortlet != null ) && ( strIdPortlet.length( ) > 0 ) && ( strTerms != null )
                && ( !"".equals( strTerms ) ) )
        {
            return true;
        }

        return false;
    }

    /**
     * Returns the Content Service name
     *
     * @return The name as a String
     */
    public String getName( )
    {
        return CONTENT_SERVICE_NAME;
    }

    private String getTemplatePage( Document document )
    {
        if ( document.getPageTemplateDocumentId( ) != 0 )
        {
            String strPageTemplateDocument = DocumentHome.getPageTemplateDocumentPath( document.getPageTemplateDocumentId( ) );

            return strPageTemplateDocument;
        }
        else
        {
            return TEMPLATE_DOCUMENT_PAGE_DEFAULT;
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Comments implementation
    /**
     * Gets the documents list portlet containing the document
     *
     * @param strPortletId
     *            The ID of the documents list portlet where the document has been published.
     * @param nMode
     *            The current mode.
     * @param request
     *            The Http request
     * @return The HTML code of the documents list portlet as a String
     */
    private String getPortlet( HttpServletRequest request, String strPortletId, int nMode ) throws SiteMessageException
    {
        try
        {
            int nPortletId = Integer.parseInt( strPortletId );

            Portlet portlet = PortletHome.findByPrimaryKey( nPortletId );

            if ( portlet == null )
            {
                return SolrConstants.CONSTANT_EMPTY_STRING;
            }

            String strXml = portlet.getXmlDocument( request );

            // Selection of the XSL stylesheet
            // byte[] baXslSource = portlet.getXslSource( nMode );

            // FIXME Temporary solution (see LUTECE-824)
            String strFilePath = AppPropertiesService.getProperty( PROPERTY_DEFAULT_PORTLET_DOCUMENT_LIST_XSL, CONSTANT_DEFAULT_PORTLET_DOCUMENT_LIST_XSL );

            if ( strFilePath == null )
            {
                return SolrConstants.CONSTANT_EMPTY_STRING;
            }

            if ( !strFilePath.startsWith( SLASH ) )
            {
                strFilePath = SLASH + strFilePath;
            }

            String strFileName = strFilePath.substring( strFilePath.lastIndexOf( SLASH ) + 1 );
            strFilePath = strFilePath.substring( 0, strFilePath.lastIndexOf( SLASH ) + 1 );

            try ( InputStream isXsl = AppPathService.getResourceStream( strFilePath, strFileName ) )
            {
                Source xslSource = new StreamSource( isXsl );

                // Get request paramaters and store them in a map
                Enumeration<String> enumParam = request.getParameterNames( );
                Map<String, String> htParamRequest = new HashMap<>( );
                String paramName = "";

                while ( enumParam.hasMoreElements( ) )
                {
                    paramName = enumParam.nextElement( );
                    htParamRequest.put( paramName, request.getParameter( paramName ) );
                }

                Properties outputProperties = ModeHome.getOuputXslProperties( nMode );

                // Add a path param for choose url to use in admin or normal mode
                if ( nMode != MODE_ADMIN )
                {
                    htParamRequest.put( PARAMETER_SITE_PATH, AppPathService.getPortalUrl( ) );
                }
                else
                {
                    htParamRequest.put( PARAMETER_SITE_PATH, AppPathService.getAdminPortalUrl( ) );
                    htParamRequest.put( MARKER_TARGET, TARGET_TOP );
                }

                return _xmlTransformerService.transformBySourceWithXslCache( strXml, xslSource, XSLSOURCE_STYLE_PREFIX_ID + strPortletId + "-" + nMode,
                        htParamRequest, outputProperties );
            }
            catch( ResourceNotFoundException | IOException e )
            {
                AppLogService.error( "[SolrDocumentContentService] Unable to read the portlet XSL {}{}", strFilePath, strFileName, e );

                return SolrConstants.CONSTANT_EMPTY_STRING;
            }
        }
        catch( NumberFormatException e )
        {
            return SolrConstants.CONSTANT_EMPTY_STRING;
        }
    }

    /**
     * Gets the category list portlet linked with the document
     *
     * @param request
     *            The Http request
     * @param document
     *            The document
     * @param nPortletId
     *            The ID of the documents list portlet where the document has been published.
     * @param nMode
     *            The current mode.
     * @return The HTML code of the categories list portlet as a String
     */
    private String getRelatedDocumentsPortlet( HttpServletRequest request, Document document, int nPortletId, int nMode )
    {
        if ( ( nMode != MODE_ADMIN ) && ( document.getCategories( ) != null ) && ( document.getCategories( ).size( ) > 0 ) )
        {
            Map<String, Object> model = new HashMap<String, Object>( );
            List<Document> listRelatedDocument = DocumentHome.findByRelatedCategories( document, request.getLocale( ) );

            List<Document> listDocument = new ArrayList<Document>( );
            ReferenceList listDocumentPortlet = new ReferenceList( );

            // Create list of related documents from the specified categories of input document
            for ( Document relatedDocument : listRelatedDocument )
            {
                // Get list of portlets for each document
                for ( Portlet portlet : _publishingService.getPortletsByDocumentId( Integer.toString( relatedDocument.getId( ) ) ) )
                {
                    // Check if document and portlet are published and document is not the input document
                    if ( ( _publishingService.isPublished( relatedDocument.getId( ), portlet.getId( ) ) )
                            && ( portlet.getStatus( ) == Portlet.STATUS_PUBLISHED ) && ( relatedDocument.isValid( ) )
                            && ( relatedDocument.getId( ) != document.getId( ) ) )
                    {
                        listDocumentPortlet.addItem( Integer.toString( relatedDocument.getId( ) ), Integer.toString( portlet.getId( ) ) );
                        listDocument.add( relatedDocument );

                        break;
                    }
                }
            }

            model.put( MARK_DOCUMENT_CATEGORIES_LIST, listDocument );
            model.put( MARK_PORTLET_ID_LIST, listDocumentPortlet );

            HtmlTemplate templateComments = AppTemplateService.getTemplate( TEMPLATE_DOCUMENT_CATEGORIES, request.getLocale( ), model );

            return templateComments.getHtml( );
        }
        else
        {
            return SolrConstants.CONSTANT_EMPTY_STRING;
        }
    }
}
