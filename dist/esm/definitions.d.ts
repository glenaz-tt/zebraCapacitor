export interface ZebraPrinter {
    name: string;
    address: string;
    manufacturer: string;
    modelNumber: string;
    connected: boolean;
}
export interface ZebraDiscoveryResult {
    printers: Array<ZebraPrinter>;
}
export interface ZebraPrinterStatus {
    connected: boolean;
    isReadyToPrint?: boolean;
    isPaused?: boolean;
    isReceiveBufferFull?: boolean;
    isRibbonOut?: boolean;
    isPaperOut?: boolean;
    isHeadTooHot?: boolean;
    isHeadOpen?: boolean;
    isHeadCold?: boolean;
    isPartialFormatInProgress?: boolean;
}
export interface ZebraProbeLinkOsResult {
    linkOsIsAvailable: boolean;
    linkOsVersion: string;
}
export interface ZebraCapacitorPluginInterface {
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
    discover(): Promise<ZebraDiscoveryResult>;
    printerStatus(): Promise<ZebraPrinterStatus>;
    print(options: {
        cpcl: string;
    }): Promise<any>;
    isConnected(): Promise<boolean>;
    connect(options: {
        MACAddress: string;
    }): Promise<boolean>;
    disconnect(): Promise<boolean>;
    probeLinkOs(options: {
        MACAddress: string;
    }): Promise<ZebraProbeLinkOsResult>;
}
