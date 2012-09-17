package org.jboss.picketlink.cdi.permission.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.picketlink.cdi.permission.annotations.AllowedPermission;
import org.jboss.picketlink.cdi.permission.annotations.AllowedPermissions;
import org.jboss.picketlink.cdi.permission.spi.PermissionHandler;

/**
 * Stored resource permissions can either be persisted as a comma-separated list of values, or as a
 * bit-masked numerical value where each bit represents a specific permission for that class. This
 * is a helper class that handles the conversion automatically and presents a unified API for
 * dealing with these persistent actions.
 *
 * @author Shane Bryzak
 */
public abstract class BaseAbstractPermissionHandler implements PermissionHandler 
{
    private Map<Class<?>, Boolean> usesMask = new HashMap<Class<?>, Boolean>();
    
    private Map<Class<?>, Map<String, Long>> classPermissions = new HashMap<Class<?>, Map<String, Long>>();

    private synchronized void initClassPermissions(Class<?> cls) 
    {
        if (!classPermissions.containsKey(cls)) 
        {
            Map<String, Long> actions = new HashMap<String, Long>();

            boolean useMask = false;

            AllowedPermissions p = (AllowedPermissions) cls.getAnnotation(AllowedPermissions.class);
            
            if (p != null) 
            {
                AllowedPermission[] permissions = p.value();
                if (permissions != null) 
                {
                    for (AllowedPermission permission : permissions) 
                    {
                        actions.put(permission.name(), permission.mask());

                        if (permission.mask() != 0) 
                        {
                            useMask = true;
                        }
                    }
                }
            }

            // Validate that all actions have a proper mask
            if (useMask) 
            {
                Set<Long> masks = new HashSet<Long>();

                for (String action : actions.keySet()) 
                {
                    Long mask = actions.get(action);
                    if (masks.contains(mask)) 
                    {
                        throw new IllegalArgumentException("Class " + cls.getName() +
                                " defines a duplicate mask for permission action [" + action + "]");
                    }

                    if (mask == 0) 
                    {
                        throw new IllegalArgumentException("Class " + cls.getName() +
                                " must define a valid mask value for action [" + action + "]");
                    }

                    if ((mask & (mask - 1)) != 0) 
                    {
                        throw new IllegalArgumentException("Class " + cls.getName() +
                                " must define a mask value that is a power of 2 for action [" + action + "]");
                    }

                    masks.add(mask);
                }
            }

            usesMask.put(cls, useMask);
            classPermissions.put(cls, actions);
        }
    }

    protected class PermissionSet 
    {
        private Set<String> members = new HashSet<String>();
        
        private Class<?> resourceClass;

        public PermissionSet(Class<?> resourceClass, String members) 
        {
            this.resourceClass = resourceClass;
            addMembers(members);
        }

        public void addMembers(String members)
        {
            if (members == null) return;

            if (usesMask.get(resourceClass)) 
            {
                // bit mask-based actions
                long vals = Long.valueOf(members);

                Map<String, Long> permissions = classPermissions.get(resourceClass);
                for (String permission : permissions.keySet()) 
                {
                    long mask = permissions.get(permission).longValue();
                    if ((vals & mask) != 0) 
                    {
                        this.members.add(permission);
                    }
                }
            } 
            else 
            {
                // comma-separated string based actions
                String[] permissions = members.split(",");
                for (String permission : permissions) 
                {
                    this.members.add(permission);
                }
            }
        }

        public boolean contains(String action) 
        {
            return members.contains(action);
        }

        public PermissionSet add(String action) 
        {
            members.add(action);
            return this;
        }

        public PermissionSet remove(String action) 
        {
            members.remove(action);
            return this;
        }

        public Set<String> members() 
        {
            return members;
        }

        public boolean isEmpty() 
        {
            return members.isEmpty();
        }

        @Override
        public String toString() 
        {
            if (usesMask.get(resourceClass)) 
            {
                Map<String, Long> actions = classPermissions.get(resourceClass);
                long mask = 0;

                for (String member : members) 
                {
                    mask |= actions.get(member).longValue();
                }

                return "" + mask;
            } 
            else 
            {
                StringBuilder sb = new StringBuilder();
                for (String member : members) 
                {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(member);
                }
                return sb.toString();
            }
        }
    }

    public PermissionSet createPermissionSet(Class<?> resourceClass, String members) 
    {
        if (!classPermissions.containsKey(resourceClass)) 
        {
            initClassPermissions(resourceClass);
        }

        return new PermissionSet(resourceClass, members);
    }

    @Override
    public Set<String> listAvailablePermissions(Class<?> resourceClass) 
    {
        if (!classPermissions.containsKey(resourceClass)) initClassPermissions(resourceClass);
        
        Set<String> permissions = new HashSet<String>();
        
        for (String permission : classPermissions.get(resourceClass).keySet()) 
        {
            permissions.add(permission);
        }

        return permissions;
    }
}