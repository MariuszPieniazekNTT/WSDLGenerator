package com.wsdlgenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

import com.google.appengine.api.datastore.Blob;
import com.google.appengine.api.quota.QuotaService;
import com.google.appengine.api.quota.QuotaServiceFactory;
import com.wsdlgenerator.model.ExcelFile;
import com.wsdlgenerator.model.GeneratedFile;
import com.wsdlgenerator.util.CommonUtil;
import com.wsdlgenerator.util.ExcelParser;

/**
 * @author Kevin.C
 * 
 */

public class UploadServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4686314267448696575L;
	private static final Logger LOGGER = Logger.getLogger(UploadServlet.class
			.getName());

	@SuppressWarnings("unchecked")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		QuotaService quotaService = QuotaServiceFactory.getQuotaService();
		long start = quotaService.getCpuTimeInMegaCycles();

		ServletFileUpload fileUpload = new ServletFileUpload();

		try {
			FileItemIterator itemIterator = fileUpload.getItemIterator(req);
			List<ExcelFile> excelFiles = new ArrayList<ExcelFile>();
			List<GeneratedFile> generatedFiles = new ArrayList<GeneratedFile>();

			while (itemIterator.hasNext()) {
				FileItemStream itemStream = itemIterator.next();
				InputStream stream = itemStream.openStream();

				byte[] bs = new byte[8192];

				ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();

				int len;
				while ((len = stream.read(bs, 0, bs.length)) != -1) {
					arrayOutputStream.write(bs, 0, len);
				}

				Blob blob = new Blob(arrayOutputStream.toByteArray());
				stream.close();
				arrayOutputStream.close();

				ExcelFile excelFile = new ExcelFile();
				ExcelParser excelParser = new ExcelParser();
				// GeneratedFile generatedFile = new GeneratedFile();
				List<Object> list = excelParser.getReqAndRespAndServFromExcel(
						blob, itemStream.getName());

				excelFile.setName(itemStream.getName());
				excelFile.setBlob(blob);
				excelFile.setServices((Set<String>) list.get(0));
				excelFile.setRequestMsg((Map<String, Collection<?>>) list
						.get(1));
				excelFile.setResponseMsg((Map<String, Collection<?>>) list
						.get(2));

				excelFiles.add(excelFile);

			}

			// generate wsdl file
			generatedFiles.addAll(AbstractGenerator
					.getWSDLGenerator(excelFiles).getGeneratedFiles());

			// generate schema file
			generatedFiles.addAll(AbstractGenerator.getSchemaGenerator(
					excelFiles).getGeneratedFiles());

			CommonUtil.getCache().put(req.getSession().getId(),
					CommonUtil.toZip(generatedFiles));

			long end = quotaService.getCpuTimeInMegaCycles();

			// return spending time and session id
			resp.getWriter().write(
					Double.toString(quotaService
							.convertMegacyclesToCpuSeconds(end - start))
							+ ","
							+ req.getSession().getId());

		} catch (FileUploadException e) {
			LOGGER.warning(e.toString());
		}
		// TODO
	}

}
