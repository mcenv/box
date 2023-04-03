import { commands, ThemeIcon, ExtensionContext, ProviderResult, RelativePattern, TreeDataProvider, TreeItem, window, workspace } from "vscode";
import { LanguageClient, LanguageClientOptions, ServerOptions } from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export function activate(context: ExtensionContext) {
  const folder = workspace.workspaceFolders!![0];
  const serverOptions: ServerOptions = {
    command: `mcx${process.platform === "win32" ? ".bat" : ""}`,
    args: ["lsp"],
    options: {
      cwd: folder.uri.fsPath,
    },
  };
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{
      scheme: "file",
      language: "mcx",
      pattern: `${folder.uri.fsPath}/src/**/*.mcx`,
    }],
    synchronize: {
      fileEvents: workspace.createFileSystemWatcher(new RelativePattern(folder, "pack.json")),
    },
    outputChannel: window.createOutputChannel("mcx"),
  };
  client = new LanguageClient(
    "mcx",
    serverOptions,
    clientOptions,
  );
  client.start();

  commands.registerCommand("mcx.build", async () => {
    const success = await client!.sendRequest<boolean>("mcx/build");
    window.showInformationMessage(success ? "Build succeeded" : "Build failed");
  });

  window.registerTreeDataProvider("mcx.tasks", new class implements TreeDataProvider<string> {
    getTreeItem(element: string): TreeItem {
      return {
        label: element,
        iconPath: new ThemeIcon("wrench"),
        command: { title: element, command: `mcx.${element}` },
      };
    }
    getChildren(element?: string): ProviderResult<string[]> {
      return [
        "build",
      ];
    }
  });
}

export function deactivate() {
  return client?.stop();
}
