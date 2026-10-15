# Local HTTPS for iPhone and iPad

These steps enable HTTPS access to a SmartCheck instance running on Tomcat on
the same Wi-Fi network as your iPhone or iPad. They use
[mkcert](https://github.com/FiloSottile/mkcert) to create a locally trusted
certificate. This setup is for private development use, not public production.

## 1. Find the Tomcat computer's Wi-Fi IP address

On the Windows computer running Tomcat, open PowerShell and run:

```powershell
ipconfig
```

Find the **IPv4 Address** for the Wi-Fi adapter, for example `192.168.1.25`.
Reserve this address in your router if possible, so it does not change.

## 2. Install mkcert and create a certificate

Install mkcert on the Tomcat computer using its Windows instructions, then
open PowerShell and run the following commands. Replace the example IP with
the computer's Wi-Fi IPv4 address:

```powershell
mkcert -install
mkcert -pkcs12 -p12-file smartcheck.p12 192.168.1.25
```

The `-install` command adds mkcert's local root CA to the Windows trust store.
The second command creates a PKCS#12 certificate valid for the specified IP.

Copy `smartcheck.p12` into Tomcat's `conf` directory. In
`conf\server.xml`, update the certificate settings inside the existing
`<SSLHostConfig>` for the 8443 HTTPS connector:

```xml
<Certificate certificateKeystoreFile="conf/smartcheck.p12"
             certificateKeystorePassword="changeit"
             certificateKeystoreType="PKCS12" />
```

Keep the existing connector and its other settings, including the
`<SSLHostConfig>` and HTTP/2 `<UpgradeProtocol>`. mkcert's PKCS#12 output
uses the password `changeit` by default. Protect the keystore file and do not
commit it to source control. Restart Tomcat after editing `server.xml`.

## 3. Trust the local CA on the Apple devices

On the Tomcat computer, run:

```powershell
mkcert -CAROOT
```

This prints the folder containing mkcert's root CA. Transfer **only**
`rootCA.pem` from that folder to each iPhone and iPad (for example, using
AirDrop). **Never transfer or share `rootCA-key.pem`**; it can issue
certificates trusted by devices that trust this CA.

On each device, open `rootCA.pem`, install the downloaded profile in Settings,
then enable full trust under **Settings → General → About → Certificate Trust
Settings** for that root certificate.

## 4. Allow the connection and open SmartCheck

If necessary, allow inbound TCP port **8443** through Windows Defender
Firewall for the **Private** network profile. Confirm both Apple devices are
connected to the same Wi-Fi network as the Tomcat computer.

Open Safari on each device and visit the computer's IP address, replacing
the example with the address from step 1:

```text
https://192.168.1.25:8443/
```

If it does not connect, check that Tomcat is running with the HTTPS connector
enabled on port 8443, that the firewall permits the connection, and that the
devices are on the same Wi-Fi network.
