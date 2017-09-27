/**
 * Copyright 2013-2016 SmartBear Software, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smartbear.swagger;

import com.eviware.soapui.analytics.Analytics;
import com.eviware.soapui.impl.rest.RestService;
import com.eviware.soapui.impl.rest.RestServiceFactory;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.support.ModelSupport;
import com.eviware.soapui.plugins.ActionConfiguration;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.action.support.AbstractSoapUIAction;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormOptionsField;
import com.eviware.x.form.support.ADialogBuilder;
import com.eviware.x.form.support.AField;
import com.eviware.x.form.support.AField.AFieldType;
import com.eviware.x.form.support.AForm;
import com.eviware.x.form.support.XFormRadioGroup;
import com.eviware.x.impl.swing.JTextFieldFormField;

import javax.swing.JRadioButton;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Shows a simple dialog for specifying the swagger definition and performs the
 * import
 *
 * @author Ole Lensmar
 */

@ActionConfiguration(actionGroup = "EnabledWsdlProjectActions", afterAction = "AddSwaggerAction")
public class ExportSwaggerAction extends AbstractSoapUIAction<WsdlProject> {
    private static final String BASE_PATH = Form.class.getName() + Form.BASEPATH;
    private static final String TARGET_PATH = Form.class.getName() + Form.FOLDER;
    private static final String FORMAT = Form.class.getName() + Form.FORMAT;
    private static final String VERSION = Form.class.getName() + Form.VERSION;
    private static final String SWAGGER_VERSION = Form.class.getName() + Form.SWAGGER_VERSION;

    private static final String SWAGGER_1_2 = "Swagger 1.2";
    private static final String SWAGGER_2_0 = "Swagger 2.0";
    private static final String OPEN_API_3_0 = "OpenAPI 3.0";

    private XFormDialog dialog;

    public ExportSwaggerAction() {
        super("Export Swagger/OpenAPI Definition", "Creates a Swagger/OpenAPI definition for selected REST APIs");
    }

    public void perform(WsdlProject project, Object param) {
        if (project.getInterfaces(RestServiceFactory.REST_TYPE).isEmpty()) {
            UISupport.showErrorMessage("Project is missing REST APIs");
            return;
        }

        // initialize form
        XmlBeansSettingsImpl settings = project.getSettings();
        if (dialog == null) {
            dialog = ADialogBuilder.buildDialog(Form.class);

            dialog.setValue(Form.FORMAT, settings.getString(FORMAT, "json"));
            dialog.setValue(Form.VERSION, settings.getString(VERSION, "1.0"));
            dialog.setValue(Form.BASEPATH, settings.getString(BASE_PATH, ""));
            dialog.setValue(Form.FOLDER, settings.getString(TARGET_PATH, ""));
            dialog.setValue(Form.SWAGGER_VERSION, settings.getString(SWAGGER_VERSION, OPEN_API_3_0));
        }

        XFormRadioGroup radioGroup = (XFormRadioGroup) dialog.getFormField(Form.SWAGGER_VERSION);
        final JRadioButton radioButton = radioGroup.getComponentFromGroup(OPEN_API_3_0);
        hideOrShowFields(radioButton.isSelected(), dialog);
        radioButton.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                hideOrShowFields(radioButton.isSelected(), dialog);
            }
        });

        XFormOptionsField apis = (XFormOptionsField) dialog.getFormField(Form.APIS);
        apis.setOptions(ModelSupport.getNames(project.getInterfaces(RestServiceFactory.REST_TYPE)));

        while (dialog.show()) {
            try {
                Object[] options = ((XFormOptionsField) dialog.getFormField(Form.APIS)).getSelectedOptions();
                if (options.length == 0) {
                    throw new Exception("You must select at least one REST API ");
                }

                RestService[] services = new RestService[options.length];
                for (int c = 0; c < options.length; c++) {
                    services[c] = (RestService) project.getInterfaceByName(String.valueOf(options[c]));
                    if (services[c].getEndpoints().length == 0) {
                        throw new Exception("Selected APIs must contain at least one endpoint");
                    }
                }

                // double-check
                if (services.length == 0) {
                    throw new Exception("You must select at least one REST API to export");
                }

                String swaggerVersion = dialog.getValue(Form.SWAGGER_VERSION);
                String format = dialog.getValue(Form.FORMAT);

                if (format.equals("xml") && (swaggerVersion.equals(SWAGGER_2_0) || swaggerVersion.equals(OPEN_API_3_0))) {
                    throw new Exception("XML format is only supported for Swagger Version 1.2");
                }

                if (format.equals("yaml") && swaggerVersion.equals(SWAGGER_1_2)) {
                    throw new Exception("YAML format is only supported for Swagger Version 2.0 and OpenAPI 3.0.0");
                }

                String version = dialog.getValue(Form.VERSION);
                if (StringUtils.isNullOrEmpty(version)) {
                    version = "1.0";
                }

                SwaggerExporter exporter;

                if (swaggerVersion.equals(SWAGGER_1_2)) {
                    exporter = new Swagger1XExporter(project);
                } else if (swaggerVersion.equals(SWAGGER_2_0)) {
                    exporter = new Swagger2Exporter(project);
                } else {
                    exporter = new OpenAPI3Exporter(project);
                }


                String path = exporter.exportToFolder(dialog.getValue(Form.FOLDER), version,
                        format, services, dialog.getValue(Form.BASEPATH));

                UISupport.showInfoMessage("Swagger resource listing has been created at [" + path + "]");

                settings.setString(BASE_PATH, dialog.getValue(Form.BASEPATH));
                settings.setString(TARGET_PATH, dialog.getValue(Form.FOLDER));
                settings.setString(FORMAT, dialog.getValue(Form.FORMAT));
                settings.setString(VERSION, dialog.getValue(Form.VERSION));
                settings.setString(SWAGGER_VERSION, dialog.getValue(Form.SWAGGER_VERSION));

                Analytics.trackAction("ExportSwagger", "Version", dialog.getValue(Form.SWAGGER_VERSION),
                        "Format", dialog.getValue(Form.FORMAT));

                break;
            } catch (Exception ex) {
                UISupport.showErrorMessage(ex);
            }
        }
    }

    private void hideOrShowFields(boolean isSelected, XFormDialog dialog) {
        JTextFieldFormField basePath = (JTextFieldFormField) dialog.getFormField(Form.BASEPATH);
        JTextFieldFormField versionField = (JTextFieldFormField) dialog.getFormField(Form.VERSION);
        basePath.setVisible(!isSelected);
        versionField.setVisible(!isSelected);
    }

    @AForm(name = "Export Swagger/OpenAPI Definition", description = "Creates a Swagger/OpenAPI definition for selected REST APIs in this project")
    public interface Form {
        @AField(name = "APIs", description = "Select which REST APIs to include in the Swagger/OpenAPI definition", type = AFieldType.MULTILIST)
        String APIS = "APIs";

        @AField(name = "Target Folder", description = "Where to save the Swagger/OpenAPI definition", type = AFieldType.FOLDER)
        String FOLDER = "Target Folder";

        @AField(name = "API Version", description = "API Version", type = AFieldType.STRING)
        String VERSION = "API Version";

        @AField(name = "Base Path", description = "Base Path that the Swagger definition will be hosted on", type = AFieldType.STRING)
        String BASEPATH = "Base Path";

        @AField(name = "Version", description = "Select version", type = AFieldType.RADIOGROUP, values = {SWAGGER_1_2, SWAGGER_2_0, OPEN_API_3_0})
        String SWAGGER_VERSION = "Version";

        @AField(name = "Format", description = "Select format", type = AFieldType.RADIOGROUP, values = {"json", "yaml", "xml"})
        String FORMAT = "Format";
    }

}
